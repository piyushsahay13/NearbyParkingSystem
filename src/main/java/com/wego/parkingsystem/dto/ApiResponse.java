package com.wego.parkingsystem.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Standardized API response wrapper for all endpoints.
 * Successful responses populate {@code data} and {@code pagination}.
 * Error responses populate {@code error}.
 * Stale data responses additionally populate {@code warnings}.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("traceId")
    private String traceId;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private T data;

    @JsonProperty("pagination")
    private PaginationMeta pagination;

    @JsonProperty("warnings")
    private List<WarningDto> warnings;

    @JsonProperty("error")
    private ApiError error;

    // ─── Static factory helpers ───────────────────────────────────────────

    public static <T> ApiResponse<T> success(T data, String message, String traceId) {
        return ApiResponse.<T>builder()
                .success(true)
                .timestamp(Instant.now())
                .traceId(traceId)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(T data, PaginationMeta pagination, String message, String traceId) {
        return ApiResponse.<T>builder()
                .success(true)
                .timestamp(Instant.now())
                .traceId(traceId)
                .message(message)
                .data(data)
                .pagination(pagination)
                .build();
    }

    public static <T> ApiResponse<T> successWithWarnings(
            T data, PaginationMeta pagination, String message, List<WarningDto> warnings, String traceId) {
        return ApiResponse.<T>builder()
                .success(true)
                .timestamp(Instant.now())
                .traceId(traceId)
                .message(message)
                .data(data)
                .pagination(pagination)
                .warnings(warnings)
                .build();
    }

    public static <T> ApiResponse<T> error(ApiError error, String traceId) {
        return ApiResponse.<T>builder()
                .success(false)
                .timestamp(Instant.now())
                .traceId(traceId)
                .error(error)
                .build();
    }
}
