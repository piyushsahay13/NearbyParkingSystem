package com.wego.parkingsystem.service;

import com.wego.parkingsystem.client.DataGovApiClient;
import com.wego.parkingsystem.dto.DataGovApiResponse;
import com.wego.parkingsystem.exception.DatasetException;
import com.wego.parkingsystem.exception.ErrorCode;
import com.wego.parkingsystem.repository.CarparkRepository;
import com.wego.parkingsystem.util.ValueConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaticDatasetLoaderService {

    private final CarparkRepository carparkRepository;
    private final CoordinateTransformerService coordinateTransformer;
    private final DataGovApiClient dataGovApiClient;

    @Value("${app.dataset.api.resource_id:d_23f946fa557947f93a8043bbef41dd09}")
    private String resourceId;

    private final AtomicBoolean datasetReady = new AtomicBoolean(false);

    public boolean isDatasetReady() {
        return datasetReady.get();
    }

    @Async
    @Transactional
    public void loadDataset() {
        log.info("Starting static carpark dataset ingestion from data.gov.sg API (sequentially).");

        final int limit = 500;
        int offset = 0;
        int totalRecords = 0;
        boolean hasMore = true;

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger skippedCount = new AtomicInteger(0);

        while (hasMore) {
            log.info("Fetching dataset page: offset={}, limit={}", offset, limit);
            try {
                DataGovApiResponse response = dataGovApiClient.search(resourceId, offset, limit);

                if (response == null || !response.isSuccess() || response.getResult() == null) {
                    log.error("Failed to fetch data from API at offset {}. Halting dataset ingestion.", offset);
                    break;
                }

                if (totalRecords == 0) {
                    totalRecords = response.getResult().getTotal();
                    log.info("Total records to fetch: {}", totalRecords);
                }

                for (DataGovApiResponse.CarparkRecord record : response.getResult().getRecords()) {
                    try {
                        processRecord(record);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        log.warn("Skipping record due to error: {}", e.getMessage());
                        skippedCount.incrementAndGet();
                    }
                }

                offset += response.getResult().getRecords().size();
                if (offset >= totalRecords || response.getResult().getRecords().isEmpty()) {
                    hasMore = false;
                }
            } catch (Exception e) {
                log.error("Error fetching data from API: {}", e.getMessage(), e);
                break;
            }
        }

        log.info("Static dataset ingestion complete. Total={}, Inserted/Updated={}, Skipped={}",
                totalRecords, successCount.get(), skippedCount.get());
        datasetReady.set(true);
    }

    private void processRecord(DataGovApiResponse.CarparkRecord record) {
        String carparkNumber = ValueConverter.trimToEmpty(record.getCarParkNo());
        if (carparkNumber.isEmpty()) return;

        String address = ValueConverter.trimToEmpty(record.getAddress());
        double xCoord = parseDouble(record.getXCoord(), "x_coord", carparkNumber);
        double yCoord = parseDouble(record.getYCoord(), "y_coord", carparkNumber);
        String carParkType = ValueConverter.trimToEmpty(record.getCarParkType());
        String parkingSystem = ValueConverter.trimToEmpty(record.getTypeOfParkingSystem());
        String shortTermParking = ValueConverter.trimToEmpty(record.getShortTermParking());
        String freeParking = ValueConverter.trimToEmpty(record.getFreeParking());
        String nightParking = ValueConverter.trimToEmpty(record.getNightParking());
        int carParkDecks = ValueConverter.integerOrZero(record.getCarParkDecks());
        Double gantryHeight = ValueConverter.nullableDouble(record.getGantryHeight());
        String carParkBasement = ValueConverter.trimToEmpty(record.getCarParkBasement());

        double[] wgs84 = coordinateTransformer.toWGS84(xCoord, yCoord);
        double latitude = wgs84[0];
        double longitude = wgs84[1];

        carparkRepository.upsertCarpark(
                carparkNumber, address, xCoord, yCoord,
                latitude, longitude,
                carParkType, parkingSystem,
                shortTermParking, freeParking, nightParking,
                carParkDecks, gantryHeight, carParkBasement
        );
    }

    private static double parseDouble(String s, String field, String carparkNo) {
        try {
            return Double.parseDouble(ValueConverter.trimToEmpty(s));
        } catch (NumberFormatException e) {
            throw new DatasetException(ErrorCode.CP_500_103,
                    "Invalid numeric value for " + field + " in carpark " + carparkNo + ": " + s);
        }
    }
}
