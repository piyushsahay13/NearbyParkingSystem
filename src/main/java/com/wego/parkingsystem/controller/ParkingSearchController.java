package com.wego.parkingsystem.controller;

import com.wego.parkingsystem.dto.ApiResponse;
import com.wego.parkingsystem.dto.CarparkNearbyDto;
import com.wego.parkingsystem.dto.PaginationMeta;
import com.wego.parkingsystem.dto.WarningDto;
import com.wego.parkingsystem.exception.ErrorCode;
import com.wego.parkingsystem.exception.ValidationException;
import com.wego.parkingsystem.service.CarparkSearchService;
import com.wego.parkingsystem.service.CoordinateTransformerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for nearby carpark availability search.
 *
 * <p>Endpoint: {@code GET /api/v1/carparks/nearby}
 * <p>Returns ranked list of available carparks within the specified radius
 * sorted ascending by distance from the user's GPS coordinates.
 */
@RestController
@RequestMapping("/api/v1/parking/lots")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Parking Search", description = "Singapore nearby parking availability search endpoints")
public class ParkingSearchController {

    private final CarparkSearchService carparkSearchService;
    private final CoordinateTransformerService coordinateTransformerService;

    /**
     * Finds available carparks near the given GPS coordinates within the specified radius.
     *
     * @param latitude     WGS84 latitude (-90 to 90)
     * @param longitude    WGS84 longitude (-180 to 180)
     * @param radius       search radius in meters (100–10000, default 3000)
     * @param limit        max results per page (1–100, default 10)
     * @param page         0-indexed page number (default 0)
     */
    @GetMapping("/nearby")
    @Operation(
        summary = "Find nearby available carparks",
        description = "Returns a ranked list of carparks with available lots within the specified radius, " +
                      "sorted by ascending distance. Supports pagination and stale data warnings."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "Nearby carparks retrieved successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "Validation error — invalid coordinates or parameters"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429",
            description = "Rate limit exceeded — CP-429-001"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500",
            description = "Internal server error"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502",
            description = "Partner API failure — CP-502-001"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503",
            description = "Dataset loading in progress — CP-503-002")
    })
    public ResponseEntity<ApiResponse<List<CarparkNearbyDto>>> getNearbyCarparks(

        @Parameter(description = "User latitude in WGS84 decimal degrees", example = "1.3325", required = true)
        @RequestParam @NotNull
        @DecimalMin(value = "-90.0",  message = "Latitude must be between -90 and 90.")
        @DecimalMax(value = "90.0",   message = "Latitude must be between -90 and 90.")
        Double latitude,

        @Parameter(description = "User longitude in WGS84 decimal degrees", example = "103.8471", required = true)
        @RequestParam @NotNull
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180.")
        @DecimalMax(value = "180.0",  message = "Longitude must be between -180 and 180.")
        Double longitude,

        @Parameter(description = "Search radius in meters (100–10000)", example = "3000")
        @RequestParam(defaultValue = "3000")
        @Min(value = 100,   message = "Radius must be between 100 and 10000 meters.")
        @Max(value = 10000, message = "Radius must be between 100 and 10000 meters.")
        int radius,

        @Parameter(description = "Max results per page (1–100)", example = "10")
        @RequestParam(defaultValue = "10")
        @Min(value = 1,   message = "Limit must be between 1 and 100.")
        @Max(value = 100, message = "Limit must be between 1 and 100.")
        int limit,

        @Parameter(description = "0-indexed page number", example = "0")
        @RequestParam(defaultValue = "0")
        @Min(value = 0, message = "Page must be greater than or equal to 0.")
        int page

    ) {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.debug("[{}] GET /nearby lat={} lng={} r={} limit={} page={}",
                traceId, latitude, longitude, radius, limit, page);

        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || !coordinateTransformerService.isWithinSingaporeBounds(latitude, longitude)) {
            throw new ValidationException(
                    ErrorCode.CP_400_013,
                    "Latitude and longitude must identify a location within Singapore.");
        }

        Map<String, Object> result = carparkSearchService.searchNearby(
                latitude, longitude, radius, limit, page);

        @SuppressWarnings("unchecked")
        List<CarparkNearbyDto> carparks = (List<CarparkNearbyDto>) result.get("results");
        PaginationMeta pagination       = (PaginationMeta) result.get("pagination");

        @SuppressWarnings("unchecked")
        List<WarningDto> warnings       = (List<WarningDto>) result.get("warnings");

        String message = carparks.isEmpty()
                ? "No available car parks found within the specified radius."
                : ErrorCode.CP_200_001.getDefaultMessage();

        boolean hasWarnings = warnings != null && !warnings.isEmpty();

        ApiResponse<List<CarparkNearbyDto>> response = hasWarnings
                ? ApiResponse.successWithWarnings(carparks, pagination,
                        "Results returned using the latest available snapshot.", warnings, traceId)
                : ApiResponse.success(carparks, pagination, message, traceId);

        return ResponseEntity.ok(response);
    }
}
