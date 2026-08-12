# Implementation Plan

## Current Baseline

The project uses Java 21, Spring Boot 3.3, Maven, PostgreSQL/PostGIS, Redis, JPA, Resilience4j, Micrometer, OpenAPI, and Docker Compose. Maven is retained because the repository is already built around `pom.xml`; switching build tools is not required to meet the functional requirements.

Implemented capabilities include:

- PostGIS schema for static carpark data, current availability, and partitioned availability history.
- SVY21-to-WGS84 conversion, Singapore bounds checking, CSV dataset loading, and idempotent persistence.
- Scheduled live-availability ingestion with retry/circuit-breaker fallback to stale records.
- Indexed nearby search, available-lot filtering, pagination, response contracts, Redis-backed cache, and token-bucket rate limiting.
- Central exception hierarchy, standard error responses, Swagger UI, health/metrics endpoints, and unit tests for coordinate conversion and rate limiting.

## Delivery Sequence

### 1. Foundation and Data

1. Run PostgreSQL 16/PostGIS and Redis through Docker Compose.
2. Apply the database schema and load the static HDB carpark dataset during Spring startup, before the application context is refreshed.
3. Confirm transformed coordinates remain within the configured Singapore bounding box.
4. Verify the GiST and partial availability indexes with `EXPLAIN ANALYZE` for a 10 km query.

### 2. Availability Synchronization

1. Schedule the Data.gov.sg availability sync every 60 seconds through a dedicated `CompletableFuture` worker pool, so scheduler and API threads do not wait for partner calls or database work.
2. Persist only configured lot types (default `C`) through idempotent upserts.
3. Mark the current snapshot stale when the partner call fails and recover on the next successful run.
4. Replace the current HTTP adapter with dedicated OpenFeign client/DTO/mapper classes before adding additional external partners.

### 3. Public Search API

1. Validate standard coordinate/radius/pagination ranges and enforce Singapore coordinate bounds.
2. Query PostGIS with longitude-first points, exclude unavailable lots, and return distance-sorted pages.
3. Cache quantized searches for 30 seconds; evict cached search results after a successful availability sync and fall back to PostgreSQL on cache failure.
4. Apply the Redis Lua token bucket before the controller, returning the required rate-limit headers and `CP-429-001` response.

### 4. Production Hardening

1. Move `schema.sql` into versioned Flyway migrations and enable Flyway validation.
2. Add Testcontainers PostgreSQL/PostGIS integration tests and WireMock tests for the upstream API.
3. Add an explicit cache invalidation/refresh after a successful availability sync.
4. Add request correlation IDs to the logging MDC and verify Prometheus metric names and dashboards.

## Verification Checklist

- `docker compose up --build` builds and starts the app, PostgreSQL/PostGIS, and Redis.
- `GET /api/v1/parking/lots/nearby` returns only carparks with available lots inside the supplied radius.
- Invalid or out-of-Singapore coordinates return a standardized `400` response.
- The eleventh request from one client within a minute returns `429` with `Retry-After`.
- Partner failure returns stale results when available, clearly marked as stale.
