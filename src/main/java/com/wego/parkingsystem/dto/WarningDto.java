package com.wego.parkingsystem.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/** Warning entry attached to stale-data responses (e.g. CP-503-001). */
@Data
@Builder
public class WarningDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("code")
    private String code;

    @JsonProperty("message")
    private String message;
}
