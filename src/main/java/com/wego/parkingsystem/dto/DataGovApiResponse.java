package com.wego.parkingsystem.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataGovApiResponse {
    private boolean success;
    private Result result;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        @JsonProperty("resource_id")
        private String resourceId;
        private java.util.List<CarparkRecord> records;
        private int total;
        private Links _links;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CarparkRecord {
        @JsonProperty("_id")
        private int id;
        @JsonProperty("car_park_no")
        private String carParkNo;
        private String address;
        @JsonProperty("x_coord")
        private String xCoord;
        @JsonProperty("y_coord")
        private String yCoord;
        @JsonProperty("car_park_type")
        private String carParkType;
        @JsonProperty("type_of_parking_system")
        private String typeOfParkingSystem;
        @JsonProperty("short_term_parking")
        private String shortTermParking;
        @JsonProperty("free_parking")
        private String freeParking;
        @JsonProperty("night_parking")
        private String nightParking;
        @JsonProperty("car_park_decks")
        private String carParkDecks;
        @JsonProperty("gantry_height")
        private String gantryHeight;
        @JsonProperty("car_park_basement")
        private String carParkBasement;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Links {
        private String start;
        private String next;
        private String prev;
    }
}
