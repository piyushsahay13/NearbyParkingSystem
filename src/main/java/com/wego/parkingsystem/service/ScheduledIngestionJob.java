package com.wego.parkingsystem.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Schedules live availability ingestion without occupying Spring's scheduler
 * thread. Each run is submitted to a bounded, dedicated worker pool.
 */
@Component
@Slf4j
public class ScheduledIngestionJob {

    private final LiveAvailabilityService liveAvailabilityService;
    private final StaticDatasetLoaderService staticDatasetLoaderService;
    private final Executor availabilitySyncExecutor;

    public ScheduledIngestionJob(
            LiveAvailabilityService liveAvailabilityService,
            StaticDatasetLoaderService staticDatasetLoaderService,
            @Qualifier("availabilitySyncExecutor") Executor availabilitySyncExecutor) {
        this.liveAvailabilityService = liveAvailabilityService;
        this.staticDatasetLoaderService = staticDatasetLoaderService;
        this.availabilitySyncExecutor = availabilitySyncExecutor;
    }

    @Value("${app.sync.enabled:true}")
    private boolean syncEnabled;

    @Scheduled(fixedDelayString = "${app.sync.interval-ms:300000}",
               initialDelayString = "${app.sync.initial-delay-ms:15000}")
    public void runSync() {
        if (!syncEnabled) {
            log.debug("Live availability sync is disabled. Skipping.");
            return;
        }

        if (!staticDatasetLoaderService.isDatasetReady()) {
            log.info("Static dataset is not ready yet. Skipping live availability sync.");
            return;
        }

        log.debug("Scheduling asynchronous availability sync.");
        CompletableFuture.runAsync(liveAvailabilityService::syncAvailability, availabilitySyncExecutor)
                .exceptionally(error -> {
                    log.warn("Asynchronous availability sync failed: {}", error.getMessage());
                    return null;
                });
    }
}
