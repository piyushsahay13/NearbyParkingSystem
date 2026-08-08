package com.wego.parkingsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Singapore Nearby Parking Availability System
 * Entry point for the Spring Boot application.
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableAsync
@EnableFeignClients
public class ParkingSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParkingSystemApplication.class, args);
    }
}
