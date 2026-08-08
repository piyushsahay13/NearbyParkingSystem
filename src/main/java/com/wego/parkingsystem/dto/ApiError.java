package com.wego.parkingsystem.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Structured error payload embedded within {@link ApiResponse}.
 * Supports both single-field and multi-field validation error details.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    @JsonProperty("status")
    private int status;

    @JsonProperty("code")
    private String code;

    @JsonProperty("type")
    private String type;

    @JsonProperty("message")
    private String message;

    /** Present for single-field validation errors. */
    @JsonProperty("field")
    private String field;

    /** Present for multi-field validation errors (CP-400-999). */
    @JsonProperty("details")
    private List<FieldError> details;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldError {

        @JsonProperty("field")
        private String field;

        @JsonProperty("message")
        private String message;
    }
}
