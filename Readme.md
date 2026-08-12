# Singapore Nearby Parking Availability API

A production-grade, resilient Spring Boot backend that helps drivers in Singapore find available car parks near a given GPS location. It ingests HDB carpark metadata from data.gov.sg, synchronizes live lot availability on a schedule, and serves ranked proximity search results backed by PostGIS spatial queries, a Redis cache-aside layer, and Redis token-bucket rate limiting.

- **Search endpoint:** `GET /api/v1/parking/lots/nearby`
- **Stack:** Java 21, Spring Boot, Spring Data JPA, PostgreSQL 16 + PostGIS, Redis 7, Resilience4j, OpenFeign, Micrometer/Prometheus.

---

## High-Level Architecture

![hld.jpeg](hld.jpeg)

**Key points**

- **Two independent ingestion paths.** Static carpark metadata loads once at startup (`StartupDatasetLoader` -> `StaticDatasetLoaderService`, async and paginated), while live lot availability refreshes every 5 minutes (`ScheduledIngestionJob` -> `LiveAvailabilityService`).
- **Cache-aside on read.** `CarparkSearchService` checks Redis first using a spatially quantized key (~110 m grid at 3-decimal rounding), falling back to a PostGIS proximity query on a miss. Each live sync calls `@CacheEvict` to clear stale search results.
- **Resilience.** The live API call is wrapped in Resilience4j circuit breaker + retry. On failure the fallback marks all availability rows as `STALE`, so the API still responds with a data-freshness warning rather than failing.
- **Rate limiting** runs in a servlet filter before the controller, using an atomic Redis Lua token bucket, and fails open if Redis is unreachable.

---

## User Request Flow (Search Nearby)


![userFlow.jpeg](userFlow.jpeg)

---

## Background Ingestion Flow

Rendered diagram (generated from the sequence definition below):

![Background Ingestion Flow](docs/background-ingestion-flow.svg)

**Participant definitions:**

| Alias | Full name | Description |
| :--- | :--- | :--- |
| `Boot` | `ApplicationReadyEvent` | Spring Boot lifecycle event fired once the application context is fully initialized; `StartupDatasetLoader` listens for it to trigger the one-time static dataset load. |
| `SL` | `StaticDatasetLoaderService` | Service that ingests static carpark metadata from data.gov.sg in paginated batches, converts coordinates, and upserts carpark rows. |
| `DG` | `DataGovApiClient` (data.gov.sg Static API) | Feign client calling the `datastore_search` endpoint that returns HDB carpark records with SVY21 coordinates. |
| `CT` | `CoordinateTransformerService` | Converts SVY21 (EPSG:3414) easting/northing to WGS84 (EPSG:4326) latitude/longitude and validates Singapore bounds. |
| `Sch` | `ScheduledIngestionJob` | `@Scheduled` component that triggers the live availability sync every 5 minutes on a dedicated executor. |
| `LS` | `LiveAvailabilityService` | Service that fetches live lot availability, upserts it, and evicts the search cache; guarded by circuit breaker, retry, and a stale fallback. |
| `LA` | `LiveAvailabilityApiClient` (data.gov.sg Live API) | Feign client calling the `carpark-availability` endpoint that returns real-time lot counts. |
| `DB` | PostgreSQL (+ PostGIS) | Relational store holding the `carparks` and `carpark_current_availability` tables with spatial indexing. |
| `R` | Redis | In-memory store used for the search-result cache and rate-limit token buckets. |

<details>
<summary>Mermaid source</summary>

```mermaid
sequenceDiagram
 participant Boot as ApplicationReadyEvent (Boot)
 participant SL as StaticDatasetLoaderService (SL)
 participant DG as DataGovApiClient / data.gov.sg Static API (DG)
 participant CT as CoordinateTransformerService (CT)
 participant Sch as ScheduledIngestionJob (Sch)
 participant LS as LiveAvailabilityService (LS)
 participant LA as LiveAvailabilityApiClient / data.gov.sg Live API (LA)
 participant DB as PostgreSQL + PostGIS (DB)
 participant R as Redis (R)

 Note over Boot,DB: One-time startup load
 Boot->>SL: loadDataset() @Async
 loop paginated (limit 100)
 SL->>DG: search(resourceId, offset, limit)
 DG-->>SL: carpark records (SVY21)
 SL->>CT: toWGS84(x, y)
 SL->>DB: upsertCarpark(...)
 end
 SL->>SL: datasetReady = true

 Note over Sch,R: Every 5 minutes
 Sch->>SL: isDatasetReady?
 Sch->>LS: syncAvailability() (async pool)
 LS->>LA: getAvailability()
 alt success
 LA-->>LS: live lot data
 LS->>DB: upsertAvailability(...) per carpark
 LS->>R: evict carparks:search (all)
 else failure (circuit open / error)
 LS->>DB: markAllAsStale()
 Note right of LS: next reads return STALE warning
 end
```

</details>

---

## Component Overview

| Layer | Component | Responsibility |
| :--- | :--- | :--- |
| Web | `RateLimitFilter` | Per-IP token-bucket rate limiting before the controller; injects `X-RateLimit-*` headers, returns 429 on exhaustion, fails open if Redis is down. |
| Web | `ParkingSearchController` | Validates request params and Singapore bounds; delegates to search service; maps results to `ApiResponse`. |
| Web | `GlobalExceptionHandler` | Central exception-to-HTTP mapping using the `CP-*` error taxonomy. |
| Service | `CarparkSearchService` | Cache-aside proximity search (`@Cacheable`), row mapping to DTOs, pagination, stale-data warnings. |
| Service | `CoordinateTransformerService` | SVY21 (EPSG:3414) to WGS84 conversion and Singapore bounds validation. |
| Service | `LiveAvailabilityService` | Scheduled live availability sync with circuit breaker, retry, and cache eviction; stale fallback. |
| Service | `StaticDatasetLoaderService` | Async paginated ingestion of static carpark metadata at startup. |
| Service | `ScheduledIngestionJob` | Triggers live sync every 5 minutes on a dedicated executor. |
| Client | `DataGovApiClient` / `LiveAvailabilityApiClient` | Feign clients for the two data.gov.sg endpoints. |
| Repository | `CarparkRepository` | PostGIS `ST_DWithin` / `ST_Distance` proximity queries and carpark upserts. |
| Repository | `CarparkCurrentAvailabilityRepository` | Availability upserts and `markAllAsStale`. |
| Data | PostgreSQL + PostGIS | Static metadata and current availability with spatial GiST indexing. |
| Data | Redis | Search-result cache and rate-limit token buckets. |

---

## Running Locally

```bash
docker compose up --build
```

This starts three services defined in `docker-compose.yml`:

- `app` — the Spring Boot application on port `8080`
- `db` — PostgreSQL 16 + PostGIS
- `redis` — Redis 7 (cache + rate-limit store)

API docs (Swagger UI) are available at `http://localhost:8080/swagger-ui.html`, and metrics at `http://localhost:8080/actuator/prometheus`.

---

## Example Request

```bash
curl "http://localhost:8080/api/v1/parking/lots/nearby?latitude=1.3325&longitude=103.8471&radius=3000&lim…
```

| Param | Range | Default | Description |
| :--- | :--- | :--- | :--- |
| `latitude` | -90 to 90 (must be in SG bounds) | required | WGS84 latitude |
| `longitude` | -180 to 180 (must be in SG bounds) | required | WGS84 longitude |
| `radius` | 100 to 10000 (meters) | 3000 | Search radius |
| `limit` | 1 to 100 | 10 | Page size |
| `page` | >= 0 | 0 | 0-indexed page |

---

## Future Perspective and Challenges

### What was your most challenging technical or product decision, and why?

The most challenging technical decision was deciding how to design the location-based carpark search so that it works well at the current scale without over-engineering it for future scale.

A simple approach would be to query carparks based directly on latitude and longitude, but as the number of carparks and search requests grows, the amount of data involved in every location search can become expensive.

I therefore considered grid-based geographical partitioning as a future scaling strategy. The idea is to divide the map into cells, associate each carpark with a cell, and when a user searches, first identify the user's cell and nearby cells. We can then perform the exact distance calculation only on those candidate carparks.

I didn't want to introduce that complexity prematurely, because grid-based systems also introduce challenges such as boundary cases, cell-size selection, and uneven data distribution. So the decision was to keep the initial implementation simpler while designing it in a way that allows grid-based indexing to be introduced when the scale justifies it.

The key trade-off was simplicity and correctness today versus scalability tomorrow.

### If users report "nearby" results 10km away, how would you investigate and fix this?

1.  **Investigate the Request**: First, I would check the user's request parameters, specifically the `radius`, `latitude`, and `longitude`.
2.  **Verify the Result**: I would then manually verify the distance between the user's coordinates and the coordinates of the "distant" carpark result.
3.  **Verify the Distance Logic**: I would review the application's distance calculation logic to ensure it's correct.
4.  **Check the Database**: As a final step, I would query the database to see if there are any other valid carparks closer to the user that should have been returned.

### What's one aspect you'd improve given more time?

Given more time, I would improve two main aspects:

1.  **Spatial Indexing**: I would improve the spatial indexing by using a more advanced partitioning strategy like a Quadtree or Google's S2 library. This would involve dividing the world map into a grid of cells, which would help in scaling and expanding the application globally.
2.  **Monitoring and Metrics**: I would also add more detailed monitoring and metrics. This would include creating dashboards with graphs to better understand error rates, request times, and other key performance indicators.
