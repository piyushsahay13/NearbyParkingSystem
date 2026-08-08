package com.wego.parkingsystem.client;

import com.wego.parkingsystem.dto.LiveAvailabilityResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "liveAvailabilityApi", url = "${app.partner-api.carpark-availability-url}")
public interface LiveAvailabilityApiClient {

    @GetMapping
    LiveAvailabilityResponse getAvailability();
}
