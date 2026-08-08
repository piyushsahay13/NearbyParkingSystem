package com.wego.parkingsystem.service;

import com.wego.parkingsystem.dto.CarparkNearbyDto;
import com.wego.parkingsystem.dto.PaginationMeta;
import com.wego.parkingsystem.dto.WarningDto;
import com.wego.parkingsystem.exception.ErrorCode;
import com.wego.parkingsystem.exception.SynchronizationException;
import com.wego.parkingsystem.repository.CarparkRepository;
import com.wego.parkingsystem.util.ValueConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Core business service for nearby carpark proximity search.
 * Applies cache-aside via Spring Cache (@Cacheable) using quantized spatial keys.
 * On cache miss, executes PostGIS ST_DWithin query on PostgreSQL.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CarparkSearchService {

    private final CarparkRepository carparkRepository;
    private final StaticDatasetLoaderService datasetLoaderService;

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Searches for available carparks within {@code radiusMeters} of the given coordinates.
     * Results are spatially quantized before cache lookup (3 decimal place rounding ≈ 110m grid).
     *
     * @param latitude     WGS84 latitude (e.g. 1.3325)
     * @param longitude    WGS84 longitude (e.g. 103.8471)
     * @param radiusMeters search radius in meters (100–10000)
     * @param limit        max results per page (1–100)
     * @param page         0-indexed page number
     * @return map with keys: "results" (List<CarparkNearbyDto>), "pagination" (PaginationMeta), "warnings" (List<WarningDto>)
     */
    @Cacheable(
        value = "carparks:search",
        key = "'lat:' + T(java.lang.Math).round(#latitude  * 1000.0) / 1000.0 " +
              "+ ':lng:' + T(java.lang.Math).round(#longitude * 1000.0) / 1000.0 " +
              "+ ':r:' + #radiusMeters + ':p:' + #page + ':l:' + #limit",
        unless = "#result == null"
    )
    @Transactional(readOnly = true)
    public Map<String, Object> searchNearby(
            double latitude, double longitude,
            double radiusMeters, int limit, int page) {

        // Guard: reject requests if dataset is still initializing
        if (!datasetLoaderService.isDatasetReady()) {
            throw new SynchronizationException(ErrorCode.CP_503_002);
        }

        int offset = page * limit;
        List<Object[]> rows = carparkRepository.findNearbyAvailableCarparks(
                latitude, longitude, radiusMeters, limit, offset);
        if (rows == null) {
            rows = List.of();
        }

        long totalElements = carparkRepository.countNearbyAvailableCarparks(
                latitude, longitude, radiusMeters);

        List<CarparkNearbyDto> results = new ArrayList<>();
        List<WarningDto> warnings      = new ArrayList<>();
        boolean hasStaleRecords        = false;

        for (Object[] row : rows) {
            if (row == null || row.length < 16) {
                log.warn("Skipping incomplete nearby-carpark query row.");
                continue;
            }
            CarparkNearbyDto dto = mapRow(row);
            results.add(dto);
            if ("STALE".equals(dto.getDataFreshness())) {
                hasStaleRecords = true;
            }
        }

        if (hasStaleRecords) {
            warnings.add(WarningDto.builder()
                    .code(ErrorCode.CP_503_001.getCode())
                    .message(ErrorCode.CP_503_001.getDefaultMessage())
                    .build());
        }

        PaginationMeta pagination = PaginationMeta.of(page, limit, totalElements);

        return Map.of(
                "results",    results,
                "pagination", pagination,
                "warnings",   warnings
        );
    }

    // ─── Row mapping ─────────────────────────────────────────────────────────

    /**
     * Maps a native query result row to {@link CarparkNearbyDto}.
     * Column order matches the SELECT clause in {@link com.wego.parkingsystem.repository.CarparkRepository}.
     *
     * <p>Row layout:
     * [0] carpark_number, [1] address, [2] latitude, [3] longitude,
     * [4] total_lots, [5] lots_available, [6] lot_type,
     * [7] update_datetime, [8] is_stale,
     * [9] car_park_type, [10] type_of_parking_system,
     * [11] short_term_parking, [12] free_parking, [13] night_parking,
     * [14] gantry_height, [15] distance_meters
     */
    private CarparkNearbyDto mapRow(Object[] row) {
        boolean isStale = Boolean.TRUE.equals(row[8]);

        return CarparkNearbyDto.builder()
                .carParkNo(ValueConverter.stringValue(row[0]))
                .address(ValueConverter.stringValue(row[1]))
                .distanceInMeters(ValueConverter.doubleValue(row[15]))
                .availableLots(ValueConverter.integerValue(row[5]))
                .totalLots(ValueConverter.integerValue(row[4]))
                .lotType(ValueConverter.stringValue(row[6]))
                .carParkType(ValueConverter.stringValue(row[9]))
                .parkingSystem(ValueConverter.stringValue(row[10]))
                .shortTermParking(ValueConverter.stringValue(row[11]))
                .freeParking(ValueConverter.stringValue(row[12]))
                .nightParking(ValueConverter.yesOrY(ValueConverter.stringValue(row[13])))
                .gantryHeight(ValueConverter.bigDecimalOrNull(row[14]))
                .lastUpdated(ValueConverter.instantOrNow(row[7]))
                .dataFreshness(isStale ? "STALE" : "FRESH")
                .build();
    }

    // ─── Type coercion helpers ────────────────────────────────────────────────

}
