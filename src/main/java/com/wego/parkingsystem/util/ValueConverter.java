package com.wego.parkingsystem.util;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.sql.Timestamp;

/** Shared null-safe conversions for query, CSV, and partner API values. */
public final class ValueConverter {
    private ValueConverter() { }

    public static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    public static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public static int integerValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        return integerOrZero(value == null ? null : value.toString());
    }

    public static int integerOrZero(String value) {
        try {
            return Integer.parseInt(trimToEmpty(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    public static double doubleValue(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(value == null ? "" : value.toString());
        } catch (NumberFormatException exception) {
            return 0.0;
        }
    }

    public static Double nullableDouble(String value) {
        String trimmed = trimToEmpty(value);
        if (trimmed.isEmpty()) return null;
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static BigDecimal bigDecimalOrNull(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static Boolean yesOrY(String value) {
        if (value == null) return null;
        return "YES".equalsIgnoreCase(value.trim()) || "Y".equalsIgnoreCase(value.trim());
    }

    public static Instant timestampOrNow(String primaryTimestamp, String fallbackTimestamp) {
        Instant primary = parseTimestamp(primaryTimestamp, true);
        if (primary != null) return primary;
        Instant fallback = parseTimestamp(fallbackTimestamp, false);
        return fallback == null ? Instant.now() : fallback;
    }

    /** Converts database timestamp values without exposing persistence types to services. */
    public static Instant instantOrNow(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
        return Instant.now();
    }

    private static Instant parseTimestamp(String value, boolean dataGovLocalTimestamp) {
        if (value == null || value.isBlank()) return null;
        try {
            String normalized = dataGovLocalTimestamp
                    ? value.replace(" ", "T").concat("Z").replaceAll("Z+$", "Z")
                    : value;
            return Instant.parse(normalized);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
