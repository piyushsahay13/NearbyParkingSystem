package com.wego.parkingsystem.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.io.Serializable;
import java.time.Instant;

/**
 * Response DTO representing a single nearby carpark result item.
 * Matches the JSON payload contract from REQUIREMENTS.md Section 4.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CarparkNearbyDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("carParkNo")
    private String carParkNo;

    @JsonProperty("address")
    private String address;

    @JsonProperty("distanceInMeters")
    private double distanceInMeters;

    @JsonProperty("availableLots")
    private int availableLots;

    @JsonProperty("totalLots")
    private int totalLots;

    @JsonProperty("lotType")
    private String lotType;

    @JsonProperty("carParkType")
    private String carParkType;

    @JsonProperty("parkingSystem")
    private String parkingSystem;

    @JsonProperty("shortTermParking")
    private String shortTermParking;

    @JsonProperty("freeParking")
    private String freeParking;

    @JsonProperty("nightParking")
    private Boolean nightParking;

    @JsonProperty("gantryHeight")
    private BigDecimal gantryHeight;

    @JsonProperty("lastUpdated")
    private Instant lastUpdated;

    /**
     * Availability data freshness: "FRESH" or "STALE".
     * "STALE" is returned when the last ingestion sync failed and
     * the availability data may be older than 60 seconds.
     */
    @JsonProperty("dataFreshness")
    private String dataFreshness;
}
