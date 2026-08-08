package com.wego.parkingsystem.client;

import com.wego.parkingsystem.dto.DataGovApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "dataGovApi", url = "${app.dataset.api.url:https://data.gov.sg/api/action/datastore_search}")
public interface DataGovApiClient {

    @GetMapping
    DataGovApiResponse search(
            @RequestParam("resource_id") String resourceId,
            @RequestParam("offset") int offset,
            @RequestParam("limit") int limit
    );
}
