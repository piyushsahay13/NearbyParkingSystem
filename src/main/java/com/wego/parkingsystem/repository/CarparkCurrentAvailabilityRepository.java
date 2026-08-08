package com.wego.parkingsystem.repository;

import com.wego.parkingsystem.model.CarparkCurrentAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** JPA repository for real-time carpark lot availability state. */
@Repository
public interface CarparkCurrentAvailabilityRepository
        extends JpaRepository<CarparkCurrentAvailability, String> {

    /**
     * Idempotent upsert: inserts or updates current availability record.
     * Executes within a single SQL round-trip for batch efficiency.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO carpark_current_availability (
                carpark_number, total_lots, lots_available,
                lot_type, update_datetime, is_stale, updated_at
            ) VALUES (
                :carparkNumber, :totalLots, :lotsAvailable,
                :lotType, :updateDatetime, false, CURRENT_TIMESTAMP
            )
            ON CONFLICT (carpark_number) DO UPDATE SET
                total_lots = EXCLUDED.total_lots,
                lots_available = EXCLUDED.lots_available,
                lot_type = EXCLUDED.lot_type,
                update_datetime = EXCLUDED.update_datetime,
                is_stale = false,
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    void upsertAvailability(
            @Param("carparkNumber") String carparkNumber,
            @Param("totalLots") int totalLots,
            @Param("lotsAvailable") int lotsAvailable,
            @Param("lotType") String lotType,
            @Param("updateDatetime") Instant updateDatetime
    );

    /** Marks all current availability records as stale (fallback flag on API failure). */
    @Modifying
    @Transactional
    @Query(value = "UPDATE carpark_current_availability SET is_stale = true, updated_at = CURRENT_TIMESTAMP",
            nativeQuery = true)
    void markAllAsStale();

    /** Returns count of carparks with at least one available lot. */
    @Query("SELECT COUNT(a) FROM CarparkCurrentAvailability a WHERE a.lotsAvailable > 0")
    long countAvailableCarparks();
}
