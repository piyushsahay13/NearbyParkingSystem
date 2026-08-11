package com.wego.parkingsystem.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.wego.parkingsystem.util.FlexibleOffsetDateTimeDeserializer;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LiveAvailabilityResponse {
    private List<Item> items;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        @JsonDeserialize(using = FlexibleOffsetDateTimeDeserializer.class)
        private OffsetDateTime timestamp;
        @JsonProperty("carpark_data")
        private List<CarparkData> carparkData;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CarparkData {
        @JsonProperty("carpark_number")
        private String carparkNumber;
        @JsonProperty("update_datetime")
        @JsonDeserialize(using = FlexibleOffsetDateTimeDeserializer.class)
        private OffsetDateTime updateDatetime;
        @JsonProperty("carpark_info")
        private List<CarparkInfo> carparkInfo;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CarparkInfo {
        @JsonProperty("total_lots")
        private int totalLots;
        @JsonProperty("lot_type")
        private String lotType;
        @JsonProperty("lots_available")
        private int lotsAvailable;
    }
}
