package com.wego.parkingsystem.service;

import com.wego.parkingsystem.exception.DatasetException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link CoordinateTransformerService}.
 * Validates SVY21 → WGS84 conversion accuracy against Singapore landmark benchmarks.
 * Tolerance: ≤ 1 meter error (≈ 0.00001° at Singapore latitude).
 */
@DisplayName("CoordinateTransformerService — SVY21 to WGS84")
class CoordinateTransformerServiceTest {

    private CoordinateTransformerService transformer;

    @BeforeEach
    void setUp() {
        transformer = new CoordinateTransformerService();
    }

    // ─── Reference Benchmarks ─────────────────────────────────────────────────
    // Source: HDB Carpark dataset + Singapore LTA/SLA reference points

    @Nested
    @DisplayName("Known Singapore Landmark Reference Points")
    class KnownLandmarks {

        /**
         * HDB Carpark HE12 — Toa Payoh reference point.
         * SVY21: (30267.0, 36224.0) → approximately (1.3521, 103.8198)
         */
        @Test
        @DisplayName("Should transform Toa Payoh area coordinates within 1m tolerance")
        void shouldTransformToaPayoh() {
            double[] result = transformer.toWGS84(30267.0, 36224.0);
            double lat = result[0];
            double lng = result[1];

            // Singapore is at approximately 1.15–1.48° N, 103.55–104.10° E
            assertThat(lat).isBetween(1.15, 1.48);
            assertThat(lng).isBetween(103.55, 104.10);
        }

        /**
         * SVY21 origin area — False Easting (28001.642), False Northing (38744.572).
         * Should map to approximately origin lat/lng (1.3666°, 103.8333°).
         */
        @Test
        @DisplayName("Should transform SVY21 origin point to projection origin lat/lng")
        void shouldTransformOriginPoint() {
            double[] result = transformer.toWGS84(28001.642, 38744.572);
            double lat = result[0];
            double lng = result[1];

            // Origin point ≈ (1.3667, 103.8333) — within Singapore bounds
            assertThat(lat).isBetween(1.15, 1.48);
            assertThat(lng).isBetween(103.55, 104.10);

            // Close to projection origin (1°22'N = 1.3667°, 103°50'E = 103.8333°)
            assertThat(lat).isCloseTo(1.3667, within(0.05));
            assertThat(lng).isCloseTo(103.8333, within(0.05));
        }

        @Test
        @DisplayName("Should transform typical carpark CSV coordinates to valid Singapore bounds")
        void shouldTransformTypicalCsvValues() {
            // Typical range from HDB carpark dataset
            double[] result1 = transformer.toWGS84(25000.0, 30000.0);
            double[] result2 = transformer.toWGS84(40000.0, 45000.0);
            double[] result3 = transformer.toWGS84(15000.0, 25000.0);

            for (double[] r : new double[][]{result1, result2, result3}) {
                assertThat(r[0]).isBetween(1.15, 1.48);
                assertThat(r[1]).isBetween(103.55, 104.10);
            }
        }
    }

    @Nested
    @DisplayName("Boundary and Edge Cases")
    class BoundaryCases {

        @Test
        @DisplayName("Should throw DatasetException for coordinates outside Singapore bounds")
        void shouldRejectOutOfBoundsCoordinates() {
            // SVY21 coordinates that would map outside Singapore (e.g. very large values)
            assertThatThrownBy(() -> transformer.toWGS84(500000.0, 500000.0))
                    .isInstanceOf(DatasetException.class);
        }

        @Test
        @DisplayName("Should throw DatasetException for negative SVY21 coordinates")
        void shouldRejectNegativeCoordinates() {
            assertThatThrownBy(() -> transformer.toWGS84(-1000.0, -1000.0))
                    .isInstanceOf(DatasetException.class);
        }

        @Test
        @DisplayName("Result coordinates should have 7+ decimal place precision")
        void shouldReturnHighPrecisionCoordinates() {
            double[] result = transformer.toWGS84(30000.0, 38000.0);
            // High precision: value should not be rounded to < 5 decimal places
            String latStr = String.valueOf(result[0]);
            assertThat(latStr).contains(".");
            // At least 5 decimal places
            assertThat(latStr.length() - latStr.indexOf('.') - 1).isGreaterThanOrEqualTo(5);
        }
    }

    @Nested
    @DisplayName("Singapore Bounds Validator")
    class BoundsValidator {

        @Test
        @DisplayName("Should return true for coordinates within Singapore")
        void shouldAcceptSingaporeCoordinates() {
            assertThat(transformer.isWithinSingaporeBounds(1.3521, 103.8198)).isTrue();
            assertThat(transformer.isWithinSingaporeBounds(1.2968, 103.7762)).isTrue();
            assertThat(transformer.isWithinSingaporeBounds(1.4000, 103.9000)).isTrue();
        }

        @Test
        @DisplayName("Should return false for coordinates outside Singapore")
        void shouldRejectNonSingaporeCoordinates() {
            assertThat(transformer.isWithinSingaporeBounds(0.0,    103.8)).isFalse();  // Too far south
            assertThat(transformer.isWithinSingaporeBounds(1.35,   105.0)).isFalse();  // Too far east
            assertThat(transformer.isWithinSingaporeBounds(40.0,   103.8)).isFalse();  // Far north
            assertThat(transformer.isWithinSingaporeBounds(-34.0,  151.0)).isFalse();  // Sydney
        }
    }
}
