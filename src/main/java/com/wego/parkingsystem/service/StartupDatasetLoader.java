package com.wego.parkingsystem.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartupDatasetLoader implements ApplicationListener<ApplicationReadyEvent> {

    private final StaticDatasetLoaderService staticDatasetLoaderService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("Application is ready. Triggering background dataset ingestion.");
        staticDatasetLoaderService.loadDataset();
    }
}
