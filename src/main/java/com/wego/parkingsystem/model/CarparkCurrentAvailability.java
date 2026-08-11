package com.wego.parkingsystem.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity mapping to {@code carpark_current_availability} table.
 * Stores the latest known lot availability state per carpark.
 * Updated every 60 seconds by the scheduled ingestion job.
 */
@Entity
@Table(name = "carpark_current_availability")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class CarparkCurrentAvailability {

    @Id
    @Column(name = "carpark_number", nullable = false, length = 32)
    private String carparkNumber;

    @Column(name = "total_lots", nullable = false)
    private int totalLots;

    @Column(name = "lots_available", nullable = false)
    private int lotsAvailable;

    /**
     * Lot type: C = Car, Y = Motorcycle, H = Heavy Vehicle.
     */
    @Column(name = "lot_type", nullable = false, length = 10)
    private String lotType;

    @Column(name = "update_datetime", nullable = false)
    private Instant updateDatetime;

    /**
     * True when the last ingestion attempt failed and this record
     * represents a stale/cached snapshot from the previous successful sync.
     */
    @Column(name = "is_stale", nullable = false)
    @Builder.Default
    private boolean isStale = false;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
