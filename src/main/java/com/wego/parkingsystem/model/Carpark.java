package com.wego.parkingsystem.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity mapping to the {@code carparks} table.
 * Stores static carpark metadata from the HDB Singapore dataset.
 * Coordinates are stored in both SVY21 (original) and WGS84 (transformed) formats.
 */
@Entity
@Table(name = "carparks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "location")
public class Carpark {

    @Id
    @Column(name = "carpark_number", nullable = false, length = 32)
    private String carparkNumber;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "x_coord", nullable = false, precision = 12, scale = 4)
    private BigDecimal xCoord;

    @Column(name = "y_coord", nullable = false, precision = 12, scale = 4)
    private BigDecimal yCoord;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    /**
     * PostGIS GEOGRAPHY(Point, 4326) column stored as WKB string.
     * Used by native spatial queries (ST_DWithin, ST_Distance).
     * Populated via native SQL: ST_MakePoint(longitude, latitude)::geography
     */
    @Column(name = "location", columnDefinition = "geography(Point,4326)")
    private String location;

    @Column(name = "car_park_type", length = 64)
    private String carParkType;

    @Column(name = "type_of_parking_system", length = 64)
    private String typeOfParkingSystem;

    @Column(name = "short_term_parking", length = 64)
    private String shortTermParking;

    @Column(name = "free_parking", length = 64)
    private String freeParking;

    @Column(name = "night_parking", length = 16)
    private String nightParking;

    @Column(name = "car_park_decks")
    @Builder.Default
    private Integer carParkDecks = 0;

    @Column(name = "gantry_height", precision = 4, scale = 2)
    private BigDecimal gantryHeight;

    @Column(name = "car_park_basement", length = 8)
    private String carParkBasement;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
