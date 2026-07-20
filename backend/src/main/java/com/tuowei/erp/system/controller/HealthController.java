package com.tuowei.erp.system.controller;

import com.tuowei.erp.common.web.ApiResponse;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final HealthEndpoint healthEndpoint;

    public HealthController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        HealthComponent health = healthEndpoint.health();
        return ApiResponse.success(Map.of("status", health.getStatus().getCode()));
    }
}
