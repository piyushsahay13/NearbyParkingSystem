package com.wego.parkingsystem.repository;

import com.wego.parkingsystem.model.Carpark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface CarparkRepository extends JpaRepository<Carpark, String> {

    @Query(value = """
            SELECT
                c.carpark_number,
                c.address,
                c.latitude,
                c.longitude,
                a.total_lots,
                a.lots_available,
                a.lot_type,
                a.update_datetime,
                a.is_stale,
                c.car_park_type,
                c.type_of_parking_system,
                c.short_term_parking,
                c.free_parking,
                c.night_parking,
                c.gantry_height,
                ST_Distance(c.location, CAST(ST_MakePoint(:longitude, :latitude) AS geography)) AS distance_meters
            FROM carparks c
            INNER JOIN carpark_current_availability a ON c.carpark_number = a.carpark_number
            WHERE a.lots_available > 0
              AND ST_DWithin(c.location, CAST(ST_MakePoint(:longitude, :latitude) AS geography), :radiusMeters)
            ORDER BY distance_meters ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Object[]> findNearbyAvailableCarparks(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusMeters") double radiusMeters,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query(value = """
            SELECT COUNT(*)
            FROM carparks c
            INNER JOIN carpark_current_availability a ON c.carpark_number = a.carpark_number
            WHERE a.lots_available > 0
              AND ST_DWithin(c.location, CAST(ST_MakePoint(:longitude, :latitude) AS geography), :radiusMeters)
            """, nativeQuery = true)
    long countNearbyAvailableCarparks(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusMeters") double radiusMeters
    );

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO carparks (
                carpark_number, address, x_coord, y_coord,
                latitude, longitude, location,
                car_park_type, type_of_parking_system,
                short_term_parking, free_parking,
                night_parking, car_park_decks, gantry_height, car_park_basement,
                created_at, updated_at
            ) VALUES (
                :carparkNumber, :address, :xCoord, :yCoord,
                :latitude, :longitude, CAST(ST_MakePoint(:longitude, :latitude) AS geography),
                :carParkType, :typeOfParkingSystem,
                :shortTermParking, :freeParking,
                :nightParking, :carParkDecks, :gantryHeight, :carParkBasement,
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            )
            ON CONFLICT (carpark_number) DO UPDATE SET
                address = EXCLUDED.address,
                latitude = EXCLUDED.latitude,
                longitude = EXCLUDED.longitude,
                location = EXCLUDED.location,
                car_park_type = EXCLUDED.car_park_type,
                type_of_parking_system = EXCLUDED.type_of_parking_system,
                short_term_parking = EXCLUDED.short_term_parking,
                free_parking = EXCLUDED.free_parking,
                night_parking = EXCLUDED.night_parking,
                car_park_decks = EXCLUDED.car_park_decks,
                gantry_height = EXCLUDED.gantry_height,
                car_park_basement = EXCLUDED.car_park_basement,
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    void upsertCarpark(
            @Param("carparkNumber") String carparkNumber,
            @Param("address") String address,
            @Param("xCoord") double xCoord,
            @Param("yCoord") double yCoord,
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("carParkType") String carParkType,
            @Param("typeOfParkingSystem") String typeOfParkingSystem,
            @Param("shortTermParking") String shortTermParking,
            @Param("freeParking") String freeParking,
            @Param("nightParking") String nightParking,
            @Param("carParkDecks") Integer carParkDecks,
            @Param("gantryHeight") Double gantryHeight,
            @Param("carParkBasement") String carParkBasement
    );

    boolean existsByCarparkNumber(String carparkNumber);
}
