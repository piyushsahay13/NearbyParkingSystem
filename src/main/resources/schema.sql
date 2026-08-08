-- ============================================================
-- Singapore Nearby Carpark Availability System — Database Schema
-- PostgreSQL 15 + PostGIS 3.4
-- ============================================================

-- ─── Extensions ───────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ============================================================
-- Table 1: Static Carpark Metadata
-- Stores static information from Singapore HDB carpark dataset.
-- Includes transformed WGS84 lat/lng and PostGIS GEOGRAPHY column
-- for native spatial index-backed proximity queries.
-- ============================================================
CREATE TABLE IF NOT EXISTS carparks (
    carpark_number          VARCHAR(32) PRIMARY KEY,
    address                 TEXT NOT NULL,
    x_coord                 NUMERIC(12, 4) NOT NULL,         -- SVY21 Easting (meters)
    y_coord                 NUMERIC(12, 4) NOT NULL,         -- SVY21 Northing (meters)
    latitude                NUMERIC(10, 7) NOT NULL,         -- WGS84 Latitude (decimal degrees)
    longitude               NUMERIC(10, 7) NOT NULL,         -- WGS84 Longitude (decimal degrees)
    location                GEOGRAPHY(Point, 4326) NOT NULL, -- PostGIS spatial index column
    car_park_type           VARCHAR(64),
    type_of_parking_system  VARCHAR(64),
    short_term_parking      VARCHAR(64),
    free_parking            VARCHAR(64),
    night_parking           VARCHAR(16),
    car_park_decks          INT DEFAULT 0,
    gantry_height           NUMERIC(4, 2) DEFAULT 0.0,
    car_park_basement       VARCHAR(8),
    created_at              TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Spatial GiST Index: enables ST_DWithin + ST_Distance in O(log N) for any radius
CREATE INDEX IF NOT EXISTS idx_carparks_spatial_gist
    ON carparks USING GIST (location);

-- B-Tree index on WGS84 coords for bounding-box pre-filter fallback (non-PostGIS queries)
CREATE INDEX IF NOT EXISTS idx_carparks_lat_lng
    ON carparks (latitude, longitude);

-- Text search index on address for future search-by-address features
CREATE INDEX IF NOT EXISTS idx_carparks_address_trgm
    ON carparks USING GIN (address gin_trgm_ops);


-- ============================================================
-- Table 2: Real-Time Carpark Availability (Hot State)
-- Stores the LATEST known lot availability per carpark.
-- Updated every 300 seconds by the ingestion scheduler.
-- ============================================================
CREATE TABLE IF NOT EXISTS carpark_current_availability (
    carpark_number  VARCHAR(32) PRIMARY KEY
        REFERENCES carparks(carpark_number) ON DELETE CASCADE,
    total_lots      INT NOT NULL CHECK (total_lots >= 0),
    lots_available  INT NOT NULL CHECK (lots_available >= 0),
    lot_type        VARCHAR(10) NOT NULL DEFAULT 'C', -- C=Car, Y=Motorcycle, H=Heavy Vehicle
    update_datetime TIMESTAMPTZ NOT NULL,
    is_stale        BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Partial Index: Only index carparks with AVAILABLE lots (drastically cuts working set)
-- This index is hit by the hot-path proximity search query.
CREATE INDEX IF NOT EXISTS idx_current_avail_positive
    ON carpark_current_availability (carpark_number, lots_available)
    WHERE lots_available > 0;

-- Composite lookup index for join + freshness check
CREATE INDEX IF NOT EXISTS idx_current_avail_lookup
    ON carpark_current_availability (carpark_number, lots_available, is_stale);


-- ============================================================
-- Table 3: Time-Series Availability History (Cold Data / Audit)
-- Stores every recorded availability snapshot.
-- Partitioned by month (RANGE on update_datetime) to support
-- billions/trillions of rows efficiently via partition pruning.
-- ============================================================
CREATE TABLE IF NOT EXISTS carpark_availability_history (
    id              BIGSERIAL,
    carpark_number  VARCHAR(32) NOT NULL,
    total_lots      INT NOT NULL,
    lots_available  INT NOT NULL,
    lot_type        VARCHAR(10) NOT NULL,
    update_datetime TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, update_datetime)
) PARTITION BY RANGE (update_datetime);

-- Default partition to catch data outside explicit monthly partitions
CREATE TABLE IF NOT EXISTS carpark_availability_history_default
    PARTITION OF carpark_availability_history DEFAULT;

-- Automatically create 2026-08 monthly partition
CREATE TABLE IF NOT EXISTS carpark_availability_history_2026_08
    PARTITION OF carpark_availability_history
    FOR VALUES FROM ('2026-08-01 00:00:00+00') TO ('2026-09-01 00:00:00+00');

CREATE TABLE IF NOT EXISTS carpark_availability_history_2026_09
    PARTITION OF carpark_availability_history
    FOR VALUES FROM ('2026-09-01 00:00:00+00') TO ('2026-10-01 00:00:00+00');

CREATE TABLE IF NOT EXISTS carpark_availability_history_2026_10
    PARTITION OF carpark_availability_history
    FOR VALUES FROM ('2026-10-01 00:00:00+00') TO ('2026-11-01 00:00:00+00');

CREATE TABLE IF NOT EXISTS carpark_availability_history_2026_11
    PARTITION OF carpark_availability_history
    FOR VALUES FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00');

CREATE TABLE IF NOT EXISTS carpark_availability_history_2026_12
    PARTITION OF carpark_availability_history
    FOR VALUES FROM ('2026-12-01 00:00:00+00') TO ('2027-01-01 00:00:00+00');

-- BRIN index: 99% smaller than B-Tree, ideal for append-only monotonic timestamps
CREATE INDEX IF NOT EXISTS idx_history_brin_time
    ON carpark_availability_history USING BRIN (update_datetime);

-- Composite index for history lookup by carpark + time range
CREATE INDEX IF NOT EXISTS idx_history_carpark_time
    ON carpark_availability_history (carpark_number, update_datetime DESC);
