package com.wego.parkingsystem.service;

import static com.wego.parkingsystem.constants.ApplicationConstants.*;

import com.wego.parkingsystem.exception.DatasetException;
import com.wego.parkingsystem.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Converts Singapore SVY21 (EPSG:3414) projected coordinates
 * to WGS84 (EPSG:4326) latitude/longitude using calibrated
 * Transverse Mercator formulas for the Singapore projection origin.
 *
 * <p>Projection Parameters (Singapore TM/SVY21):
 * <ul>
 *   <li>Origin Latitude:  1° 22' 00" N  = 1.36666667°</li>
 *   <li>Origin Longitude: 103° 50' 00" E = 103.83333333°</li>
 *   <li>Scale Factor:     1.0</li>
 *   <li>False Easting:    28,001.642 m</li>
 *   <li>False Northing:   38,744.572 m</li>
 * </ul>
 *
 * <p>Reference: Singapore Land Authority (SLA) SVY21 technical manual.
 */
@Service
@Slf4j
public class CoordinateTransformerService {

    // ─── SVY21 Projection Constants ─────────────────────────────────────────
    /**
     * Converts SVY21 (Easting, Northing) in meters to WGS84 (latitude, longitude) in decimal degrees.
     *
     * @param xCoord SVY21 Easting value in meters (e.g. 30000.0)
     * @param yCoord SVY21 Northing value in meters (e.g. 30000.0)
     * @return double[]{latitude, longitude} in WGS84 decimal degrees
     * @throws DatasetException if the transformed coordinates fall outside Singapore bounds
     */
    public double[] toWGS84(double xCoord, double yCoord) {
        try {
            if (!Double.isFinite(xCoord) || !Double.isFinite(yCoord)) {
                throw new DatasetException(ErrorCode.CP_500_104,
                        "SVY21 coordinates must be finite numeric values.");
            }

            // Remove False Easting/Northing to get local TM coordinates
            double N = yCoord - FALSE_NORTHING;  // local Northing
            double E = xCoord - FALSE_EASTING;   // local Easting

            double n = (SEMI_MAJOR_AXIS - SEMI_MINOR_AXIS) / (SEMI_MAJOR_AXIS + SEMI_MINOR_AXIS);
            double n2 = n * n;
            double n3 = n2 * n;
            double n4 = n3 * n;

            // Meridional arc at origin
            double M0 = SEMI_MAJOR_AXIS * (1.0 - n + (5.0 / 4.0) * (n2 - n3) + (81.0 / 64.0) * n4)
                    * ORIGIN_LAT_RAD
                    - SEMI_MAJOR_AXIS * (3.0 / 2.0 * (n - n3) + (15.0 / 16.0) * (n2 - n4))
                    * Math.sin(2.0 * ORIGIN_LAT_RAD)
                    + SEMI_MAJOR_AXIS * (15.0 / 16.0 * (n2 - n3)) * Math.sin(4.0 * ORIGIN_LAT_RAD)
                    - SEMI_MAJOR_AXIS * (35.0 / 48.0 * n3) * Math.sin(6.0 * ORIGIN_LAT_RAD);

            // Footpoint latitude iteration
            double M = M0 + N / SCALE_FACTOR;
            double mu = M / (SEMI_MAJOR_AXIS * (1.0 - ECCENTRICITY_SQ / 4.0
                    - 3.0 * ECCENTRICITY_SQ * ECCENTRICITY_SQ / 64.0
                    - 5.0 * ECCENTRICITY_SQ * ECCENTRICITY_SQ * ECCENTRICITY_SQ / 256.0));

            double e1 = (1.0 - Math.sqrt(1.0 - ECCENTRICITY_SQ))
                    / (1.0 + Math.sqrt(1.0 - ECCENTRICITY_SQ));
            double e12 = e1 * e1;
            double e13 = e12 * e1;
            double e14 = e13 * e1;

            double phiFoot = mu
                    + (3.0 / 2.0 * e1 - 27.0 / 32.0 * e13) * Math.sin(2.0 * mu)
                    + (21.0 / 16.0 * e12 - 55.0 / 32.0 * e14) * Math.sin(4.0 * mu)
                    + (151.0 / 96.0 * e13) * Math.sin(6.0 * mu)
                    + (1097.0 / 512.0 * e14) * Math.sin(8.0 * mu);

            // Compute WGS84 latitude/longitude from footpoint latitude
            double sinPhi  = Math.sin(phiFoot);
            double cosPhi  = Math.cos(phiFoot);
            double tanPhi  = Math.tan(phiFoot);

            double N1 = SEMI_MAJOR_AXIS / Math.sqrt(1.0 - ECCENTRICITY_SQ * sinPhi * sinPhi);
            double T1 = tanPhi * tanPhi;
            double C1 = SECOND_ECCENTRICITY_SQ * cosPhi * cosPhi;
            double R1 = SEMI_MAJOR_AXIS * (1.0 - ECCENTRICITY_SQ)
                    / Math.pow(1.0 - ECCENTRICITY_SQ * sinPhi * sinPhi, 1.5);
            double D  = E / (N1 * SCALE_FACTOR);

            double D2 = D * D;
            double D3 = D2 * D;
            double D4 = D3 * D;
            double D5 = D4 * D;
            double D6 = D5 * D;

            double lat = phiFoot
                    - (N1 * tanPhi / R1)
                    * (D2 / 2.0
                    - (5.0 + 3.0 * T1 + 10.0 * C1 - 4.0 * C1 * C1 - 9.0 * SECOND_ECCENTRICITY_SQ) * D4 / 24.0
                    + (61.0 + 90.0 * T1 + 298.0 * C1 + 45.0 * T1 * T1
                            - 252.0 * SECOND_ECCENTRICITY_SQ - 3.0 * C1 * C1) * D6 / 720.0);

            double lng = ORIGIN_LNG_RAD
                    + (D
                    - (1.0 + 2.0 * T1 + C1) * D3 / 6.0
                    + (5.0 - 2.0 * C1 + 28.0 * T1 - 3.0 * C1 * C1
                            + 8.0 * SECOND_ECCENTRICITY_SQ + 24.0 * T1 * T1) * D5 / 120.0) / cosPhi;

            double latDeg = Math.toDegrees(lat);
            double lngDeg = Math.toDegrees(lng);

            validateSingaporeBounds(latDeg, lngDeg, xCoord, yCoord);

            return new double[]{latDeg, lngDeg};

        } catch (DatasetException e) {
            throw e;
        } catch (Exception e) {
            log.error("Coordinate transformation failed for SVY21 ({}, {}): {}", xCoord, yCoord, e.getMessage());
            throw new DatasetException(ErrorCode.CP_500_104,
                    "Coordinate conversion failed for SVY21 (" + xCoord + ", " + yCoord + "): " + e.getMessage(), e);
        }
    }

    /**
     * Validates that the transformed WGS84 coordinates fall within Singapore bounding box.
     *
     * @throws DatasetException if coordinates are outside Singapore bounds
     */
    private void validateSingaporeBounds(double lat, double lng, double origX, double origY) {
        if (lat < SG_LAT_MIN || lat > SG_LAT_MAX || lng < SG_LNG_MIN || lng > SG_LNG_MAX) {
            log.warn("Transformed coordinates ({}, {}) from SVY21 ({}, {}) fall outside Singapore bounds",
                    lat, lng, origX, origY);
            throw new DatasetException(ErrorCode.CP_500_104,
                    String.format("Transformed coordinates (%.7f, %.7f) are outside Singapore bounds. " +
                            "Possible bad SVY21 input (%.4f, %.4f).", lat, lng, origX, origY));
        }
    }

    /**
     * Validates that user-supplied WGS84 coordinates are within Singapore bounds.
     * Used for incoming API request validation.
     */
    public boolean isWithinSingaporeBounds(double latitude, double longitude) {
        return Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= SG_LAT_MIN && latitude <= SG_LAT_MAX
                && longitude >= SG_LNG_MIN && longitude <= SG_LNG_MAX;
    }
}
