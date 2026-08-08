package com.wego.parkingsystem.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.0 configuration for Swagger UI.
 * Served at: /swagger-ui.html
 * JSON spec at: /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI carparkOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Parking Availability API - Singapore")
                        .description("""
                                Production-grade RESTful API for finding available parking lots near a specified
                                location in Singapore. Ingests HDB static dataset and live availability from
                                Data.gov.sg. Supports radius filtering, proximity sorting, and pagination.
                                
                                **Rate Limiting**: 10 requests/minute per IP. Returns HTTP 429 when exceeded.
                                **Stale Data**: If live sync fails, stale results are returned with CP-503-001 warning.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Piyush")
                                .email("piyushsahay13@outlook.com"))
                        .license(new License()
                                .name("MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development")));
    }
}
