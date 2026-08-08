package com.wego.parkingsystem.exception;

/**
 * Centralized catalog of all application error codes.
 * Format: CP-{HTTP_STATUS}-{SEQUENCE}
 *
 * <p>Categories:
 * <ul>
 *   <li>CP-200-xxx  — Success codes</li>
 *   <li>CP-400-xxx  — Validation errors</li>
 *   <li>CP-404-xxx  — Not found errors</li>
 *   <li>CP-409-xxx  — Conflict errors</li>
 *   <li>CP-422-xxx  — Business validation errors</li>
 *   <li>CP-429-xxx  — Rate limit errors</li>
 *   <li>CP-500-xxx  — Internal server / database errors</li>
 *   <li>CP-502-xxx  — Partner API gateway errors</li>
 *   <li>CP-503-xxx  — Service unavailable / stale data</li>
 *   <li>CP-504-xxx  — Partner API timeout errors</li>
 * </ul>
 */
public enum ErrorCode {

    // ─── Success Codes (HTTP 200) ──────────────────────────────────────────
    CP_200_001(200, "CP-200-001", "Nearby car parks retrieved successfully."),
    CP_200_002(200, "CP-200-002", "Availability synchronized successfully."),
    CP_200_003(200, "CP-200-003", "Dataset imported successfully."),
    CP_200_004(200, "CP-200-004", "Health check successful."),

    // ─── Validation Error Codes (HTTP 400) ────────────────────────────────
    CP_400_001(400, "CP-400-001", "Latitude is missing."),
    CP_400_002(400, "CP-400-002", "Latitude must be between -90 and 90."),
    CP_400_003(400, "CP-400-003", "Longitude is missing."),
    CP_400_004(400, "CP-400-004", "Longitude must be between -180 and 180."),
    CP_400_005(400, "CP-400-005", "Radius is missing."),
    CP_400_006(400, "CP-400-006", "Radius must be between 100 and 10000 meters."),
    CP_400_007(400, "CP-400-007", "Limit is missing."),
    CP_400_008(400, "CP-400-008", "Limit must be between 1 and 100."),
    CP_400_009(400, "CP-400-009", "Page must be greater than or equal to 0."),
    CP_400_010(400, "CP-400-010", "Invalid coordinate format."),
    CP_400_011(400, "CP-400-011", "Invalid query parameter."),
    CP_400_012(400, "CP-400-012", "Duplicate query parameter."),
    CP_400_013(400, "CP-400-013", "Location must be within Singapore."),
    CP_400_999(400, "CP-400-999", "Request validation failed."),

    // ─── Business Error Codes (HTTP 404) ──────────────────────────────────
    CP_404_001(404, "CP-404-001", "Car park not found."),
    CP_404_002(404, "CP-404-002", "Availability not found."),

    // ─── Conflict Error Codes (HTTP 409) ──────────────────────────────────
    CP_409_001(409, "CP-409-001", "Dataset already imported."),
    CP_409_002(409, "CP-409-002", "Dataset version conflict."),

    // ─── Business Validation Errors (HTTP 422) ────────────────────────────
    CP_422_001(422, "CP-422-001", "No available car parks found within radius."),
    CP_422_002(422, "CP-422-002", "Search radius exceeds supported area."),

    // ─── Rate Limit Errors (HTTP 429) ─────────────────────────────────────
    CP_429_001(429, "CP-429-001", "Rate limit exceeded. Maximum 10 requests per minute per client IP."),

    // ─── Database Errors (HTTP 500) ───────────────────────────────────────
    CP_500_001(500, "CP-500-001", "Database connection failed."),
    CP_500_002(500, "CP-500-002", "Database timeout."),
    CP_500_003(500, "CP-500-003", "Database transaction failed."),
    CP_500_004(500, "CP-500-004", "Unique constraint violation."),
    CP_500_005(500, "CP-500-005", "Foreign key constraint violation."),
    CP_500_006(500, "CP-500-006", "Unable to persist availability data."),

    // ─── Dataset Errors (HTTP 500) ────────────────────────────────────────
    CP_500_101(500, "CP-500-101", "Dataset file not found."),
    CP_500_102(500, "CP-500-102", "Dataset checksum validation failed."),
    CP_500_103(500, "CP-500-103", "Dataset parsing failed."),
    CP_500_104(500, "CP-500-104", "Coordinate conversion failed."),
    CP_500_105(500, "CP-500-105", "Dataset import failed."),
    CP_500_106(500, "CP-500-106", "Dataset version mismatch."),

    // ─── Synchronization Errors (HTTP 500 / 503) ──────────────────────────
    CP_500_107(500, "CP-500-107", "Failed to update availability."),
    CP_500_108(500, "CP-500-108", "Unable to reconcile records."),

    // ─── System Errors (HTTP 500) ─────────────────────────────────────────
    CP_500_900(500, "CP-500-900", "Unexpected system exception."),
    CP_500_901(500, "CP-500-901", "Unknown application error."),
    CP_500_902(500, "CP-500-902", "Configuration error."),
    CP_500_903(500, "CP-500-903", "Service dependency unavailable."),

    // ─── Partner API Errors (HTTP 502) ────────────────────────────────────
    CP_502_001(502, "CP-502-001", "Unable to retrieve parking availability from upstream provider."),
    CP_502_002(502, "CP-502-002", "Partner returned invalid payload."),
    CP_502_003(502, "CP-502-003", "Partner returned malformed JSON."),

    // ─── Service Unavailable / Stale (HTTP 503) ───────────────────────────
    CP_503_001(503, "CP-503-001", "Availability information may be stale."),
    CP_503_002(503, "CP-503-002", "Car park dataset is still being initialized. Please try again shortly."),
    CP_503_101(503, "CP-503-101", "Synchronization already running."),
    CP_503_102(503, "CP-503-102", "Synchronization interrupted."),
    CP_503_103(503, "CP-503-103", "Synchronization partially completed."),

    // ─── Partner Timeout Errors (HTTP 504) ────────────────────────────────
    CP_504_001(504, "CP-504-001", "Partner API timeout.");

    private final int httpStatus;
    private final String code;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String code, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int getHttpStatus() { return httpStatus; }
    public String getCode() { return code; }
    public String getDefaultMessage() { return defaultMessage; }
}
