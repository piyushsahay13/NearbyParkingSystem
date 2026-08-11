package com.wego.parkingsystem.constants;

/**
 * Central application constants. Configuration values that vary by environment
 * remain in {@code application.yml}; stable protocol and coordinate values live here.
 */
public interface ApplicationConstants {

    String NEARBY_CARPARKS_PATH = "/api/v1/parking/lots/nearby";
    String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    String HEADER_X_REAL_IP = "X-Real-IP";
    String HEADER_RATE_LIMIT = "X-RateLimit-Limit";
    String HEADER_RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
    String HEADER_RATE_LIMIT_RESET = "X-RateLimit-Reset";
    String HEADER_RETRY_AFTER = "Retry-After";

    double WGS84_SEMI_MAJOR_AXIS = 6378137.0;
    double SVY21_FLATTENING = 1.0 / 298.257222101;
    double SVY21_SEMI_MINOR_AXIS = WGS84_SEMI_MAJOR_AXIS * (1.0 - SVY21_FLATTENING);
    double SVY21_ECCENTRICITY_SQUARED = 2.0 * SVY21_FLATTENING - Math.pow(SVY21_FLATTENING, 2.0);
    double SVY21_SECOND_ECCENTRICITY_SQUARED = SVY21_ECCENTRICITY_SQUARED / (1.0 - SVY21_ECCENTRICITY_SQUARED);
    double SVY21_ORIGIN_LATITUDE_RADIANS = Math.toRadians(1.0 + (22.0 / 60.0));
    double SVY21_ORIGIN_LONGITUDE_RADIANS = Math.toRadians(103.0 + (50.0 / 60.0));
    double SVY21_SCALE_FACTOR = 1.0;
    double SVY21_FALSE_EASTING = 28001.642;
    double SVY21_FALSE_NORTHING = 38744.572;
    double SINGAPORE_LATITUDE_MIN = 1.15;
    double SINGAPORE_LATITUDE_MAX = 1.48;
    double SINGAPORE_LONGITUDE_MIN = 103.55;
    double SINGAPORE_LONGITUDE_MAX = 104.10;

    // SVY21 aliases retained for the projection formula.
    double SEMI_MAJOR_AXIS = WGS84_SEMI_MAJOR_AXIS;
    double FLATTENING = SVY21_FLATTENING;
    double SEMI_MINOR_AXIS = SVY21_SEMI_MINOR_AXIS;
    double ECCENTRICITY_SQ = SVY21_ECCENTRICITY_SQUARED;
    double SECOND_ECCENTRICITY_SQ = SVY21_SECOND_ECCENTRICITY_SQUARED;
    double ORIGIN_LAT_RAD = SVY21_ORIGIN_LATITUDE_RADIANS;
    double ORIGIN_LNG_RAD = SVY21_ORIGIN_LONGITUDE_RADIANS;
    double SCALE_FACTOR = SVY21_SCALE_FACTOR;
    double FALSE_EASTING = SVY21_FALSE_EASTING;
    double FALSE_NORTHING = SVY21_FALSE_NORTHING;
    double SG_LAT_MIN = SINGAPORE_LATITUDE_MIN;
    double SG_LAT_MAX = SINGAPORE_LATITUDE_MAX;
    double SG_LNG_MIN = SINGAPORE_LONGITUDE_MIN;
    double SG_LNG_MAX = SINGAPORE_LONGITUDE_MAX;
}
