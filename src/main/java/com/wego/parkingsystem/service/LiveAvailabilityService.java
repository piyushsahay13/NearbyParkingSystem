package com.wego.parkingsystem.service;

import com.wego.parkingsystem.client.LiveAvailabilityApiClient;
import com.wego.parkingsystem.dto.LiveAvailabilityResponse;
import com.wego.parkingsystem.exception.ErrorCode;
import com.wego.parkingsystem.exception.PartnerApiException;
import com.wego.parkingsystem.exception.SynchronizationException;
import com.wego.parkingsystem.repository.CarparkCurrentAvailabilityRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class LiveAvailabilityService {

    private final CarparkCurrentAvailabilityRepository availabilityRepository;
    private final LiveAvailabilityApiClient liveAvailabilityApiClient;

    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
    private static final ZoneId SINGAPORE_ZONE_ID = ZoneId.of("Asia/Singapore");

    @CircuitBreaker(name = "liveAvailabilityApi", fallbackMethod = "syncFallback")
    @Retry(name = "liveAvailabilityApi")
    @CacheEvict(cacheNames = "carparks:search", allEntries = true)
    public void syncAvailability() {
        if (!syncInProgress.compareAndSet(false, true)) {
            log.info("Sync already in progress; skipping this scheduled cycle.");
            return;
        }

        try {
            log.debug("Starting live availability sync from partner API.");

            LiveAvailabilityResponse response = liveAvailabilityApiClient.getAvailability();

            if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
                throw new PartnerApiException(ErrorCode.CP_502_002, "Partner API returned empty or null payload.");
            }

            int processed = 0;
            for (LiveAvailabilityResponse.Item item : response.getItems()) {
                if (item == null || item.getCarparkData() == null || item.getCarparkData().isEmpty()) continue;
                for (LiveAvailabilityResponse.CarparkData carparkData : item.getCarparkData()) {
                    if (carparkData == null) continue;
                    processed += processCarparkData(carparkData);
                }
            }

            log.info("Live availability sync complete. Carparks processed: {}", processed);

        } catch (SynchronizationException | PartnerApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during live availability sync: {}", e.getMessage(), e);
            throw new PartnerApiException(ErrorCode.CP_502_001,
                    "Unexpected error fetching live availability: " + e.getMessage(), e);
        } finally {
            syncInProgress.set(false);
        }
    }

    public void syncFallback(Throwable ex) {
        log.warn("Live availability sync failed — marking all carparks as stale. Cause: {}", ex == null ? "unknown" : ex.getMessage());
        try {
            availabilityRepository.markAllAsStale();
        } catch (Exception dbEx) {
            log.error("Failed to mark carparks as stale: {}", dbEx.getMessage(), dbEx);
        } finally {
            syncInProgress.set(false);
        }
    }

    private int processCarparkData(LiveAvailabilityResponse.CarparkData data) {
        if (data == null || data.getCarparkNumber() == null || data.getCarparkNumber().isBlank()
                || data.getCarparkInfo() == null || data.getCarparkInfo().isEmpty()) return 0;

        int count = 0;
        for (LiveAvailabilityResponse.CarparkInfo info : data.getCarparkInfo()) {
            if (info == null) continue;
            try {
                availabilityRepository.upsertAvailability(
                        data.getCarparkNumber(),
                        info.getTotalLots(),
                        info.getLotsAvailable(),
                        info.getLotType(),
                        data.getUpdateDatetime().atZone(SINGAPORE_ZONE_ID).toInstant()
                );
                count++;
            } catch (Exception e) {
                log.warn("Failed to process carpark {}: {}", data.getCarparkNumber(), e.getMessage());
            }
        }
        return count;
    }
}
