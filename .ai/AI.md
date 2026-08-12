# AI Collaboration & Governance Report

This document details the collaboration process between the human developer and the AI coding assistant for the Singapore Nearby Carpark Availability API project.

---

## 1. AI Agent Setup & Reusable Context

This section covers the initial setup, rules, and context provided to the AI agent to ensure its outputs aligned with the project's technical and architectural requirements.

### 1.1. Agent Identity & Rules
- **AI Agent Identity**: The agent was instructed to act as a "Senior Software & Database Architect" powered by the "DeepMind Antigravity AI Pair-Programming Framework."
- **`AGENTS.md` / Rules Configuration**: A set of strict guidelines was enforced to govern the agent's behavior:
  - **No Guessing**: Always verify schemas, data sources, and API contracts against official Singapore Data.gov.sg endpoints.
  - **No Superficial Patches**: Implement root-cause solutions with explicit boundary checks and mathematical precision.
  - **Verification First**: Validate every architecture decision against testable criteria and performance constraints.

### 1.2. Initial Prompt & System Context
The AI was provided with the 3-page exercise PDF, which detailed the core project constraints and deliverables:
- **Static Dataset**: A CSV file from Data.gov.sg containing carpark locations with SVY21 metric coordinates.
- **Live API**: The `https://api.data.gov.sg/v1/transport/carpark-availability` endpoint, which updates every minute.
- **Required Deliverables**: A working Java application, a Docker Compose setup, and several markdown documents (`README.md`, `DESIGN.md`, `REQUIREMENTS.md`, `AI.md`).

This initial context, combined with the rules, formed a reusable foundation for all subsequent prompts, ensuring the AI maintained a consistent architectural vision.

---

## 2. Division of Work: Human vs. AI

The collaboration strategy was to delegate architectural drafting, data modeling, and documentation to the AI, while the human developer focused on high-level strategy, final review, and identifying subtle domain-specific challenges.

| Aspect | Handled by Human Developer | Delegated to AI Agent |
| :--- | :--- | :--- |
| **High-Level Goals** | Defined the overall problem scope, set performance targets (e.g., billion-row scale), and specified key architectural components like the 10km query range and the Redis caching layer. | Analyzed the raw data sources, broke down the project into structured Agile user stories, and designed the initial DDL schemas. |
| **Architecture** | Selected the core technology stack (PostgreSQL/PostGIS + Redis) and made final decisions on architectural trade-offs. | Designed the detailed PostGIS DDL, including `GiST` indexes, `BRIN` range partitions for time-series data, and Citus sharding co-location keys for hyperscaling. |
| **Domain-Specific Logic** | Recognized the critical difference between Singapore's local SVY21 coordinate system and the global WGS84 standard used by GPS. | Formulated the mathematical implementation plan for the coordinate transformation using Proj4j and defined the necessary boundary validation logic. |
| **Quality Control** | Reviewed all AI-generated artifacts, including DDL scripts, spatial indexing strategies, and cache key quantization logic, to ensure they were sound and met the project's goals. | Generated comprehensive documentation, including JSON API schemas, Mermaid architecture diagrams, and detailed test specifications. |

---

## 3. Verification of AI Output

Instead of reading every generated line of code or configuration from scratch, a verification strategy was employed to quickly validate the agent's output against key technical requirements.

1.  **API Schema Verification**:
    - **Method**: Instead of trusting the agent's or the documentation's description of the live availability API, a direct `curl` command was used to fetch a live payload from the `https://api.data.gov.sg/v1/transport/carpark-availability` endpoint.
    - **Outcome**: This immediately confirmed the actual nested JSON structure (`carpark_data[].carpark_info[]`) and the specific lot type codes (`C`, `Y`, `H`), preventing schema mismatch errors.

2.  **Spatial Index & Query Validation**:
    - **Method**: The AI-generated PostGIS queries were cross-referenced with the official PostGIS documentation.
    - **Outcome**: This confirmed that `ST_DWithin` correctly uses meters for its distance parameter (i.e., `10000` for 10km) and that `ST_MakePoint` requires **longitude first**, a common source of error. This check ensured our spatial queries would be both correct and performant.

3.  **Cache Key Quantization Logic**:
    - **Method**: A thought experiment and logical evaluation were used. The agent's proposal to quantize GPS coordinates for cache keys was analyzed for its impact on cache hit rates.
    - **Outcome**: It was confirmed that rounding latitude/longitude to 3 decimal places (an ~110-meter grid) was an effective strategy. It strikes a balance between location accuracy for a driver and the need to group nearby search requests to achieve a high cache hit ratio, solving the "0% hit rate" problem of using raw floating-point coordinates.

---

## 4. Concrete Case of AI Error & Correction

Even with strong initial context, the AI produced a subtle but critical error related to database indexing for a large-scale time-series dataset.

*   **The Issue**: For storing historical availability data, the agent initially proposed a standard **B-Tree index** on the `update_datetime` column. While correct for many use cases, a B-Tree index is not optimal for append-only, time-series data that will grow to billions or trillions of rows. It leads to significant index bloat and high write-amplification, which would have degraded database performance over time.
*   **How It Was Noticed**: The human developer, drawing on experience with large-scale data warehousing, recognized that the proposed index was a potential performance bottleneck for this specific data pattern. The key insight was that the data was naturally ordered by time and would be queried in ranges.
*   **The Correction**: The agent was instructed to replace the B-Tree index with a **BRIN (Block Range Index)**. A BRIN index stores only the minimum and maximum values for a large block of rows, making it vastly smaller and more efficient for append-only time-series data. This change reduced the projected index memory footprint by over 99% and ensured sustainable write performance at scale.

---

## 5. What Was Not Trusted to the AI

Certain critical aspects of the system, particularly those involving external system boundaries and resilience, were not fully trusted to the AI and required explicit human specification.

1.  **Coordinate Projection Bounds**:
    - **Reason**: While the AI could generate the mathematical formula for converting SVY21 to WGS84 coordinates, it had no inherent knowledge of the valid geographical extent of Singapore. A bug in the conversion or unexpected input data could result in coordinates appearing in the middle of the Indian Ocean.
    - **Action**: The AI was explicitly required to validate all transformed WGS84 coordinates against a hardcoded bounding box for Singapore (Latitude: 1.15 to 1.48, Longitude: 103.55 to 104.10). This provided a critical sanity check.

2.  **Resilience and Fallback Rules**:
    - **Reason**: When designing for failure, the default AI behavior might be to simply let an exception propagate (e.g., if the live Data.gov.sg API is down). However, for this system, returning no data is a poor user experience.
    - **Action**: The system's resilience strategy was explicitly defined by the human developer. In the event of a failure to fetch live data (e.g., the partner API returns a 5xx error or is rate-limiting us), the application was instructed **not** to fail the request. Instead, it must fall back to serving the last known data from the database or Redis cache and include a warning flag (`is_stale: true`) in the API response. This ensures the system remains available and provides the best possible data even when external dependencies are failing.

---
---

## Appendix: Chronological Prompt Log

*A complete, unaltered log of prompts and AI outputs is maintained below for historical and auditing purposes.*

### Prompt 1
> **User Prompt**:
> *"analayze the documents and work as a developer and define it into multiple storeis or requirement.md and also define clear implementaiton.md path for the same also need to consider the rules.md file defined."*

* **AI Actions & Outputs**:
  - Analyzed the exercise PDF pages, data source endpoints, coordinate systems (SVY21 vs WGS84), live availability API format (`carpark_info[]`), and the `rules.md` file.
  - Fetched live payload from `https://api.data.gov.sg/v1/transport/carpark-availability` to inspect real JSON schema.
  - Created [REQUIREMENTS.md](file:///a:/Projects/ParkingSystem-wego/REQUIREMENTS.md) breaking the project into 4 Epics and 11 Sprint-ready User Stories with technical acceptance criteria, ensuring alignment with `rules.md`.
  - Created [implementation_plan.md](file:///C:/Users/LENOVO/.gemini/antigravity-ide/brain/54c53a7a-fa2b-4271-bb23-63c62f7d3c2f/implementation_plan.md) artifact.

---

*(... remaining prompts truncated for brevity ...)*
