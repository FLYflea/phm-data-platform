package com.phm.computation.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "data-computation");
        response.put("port", 8102);
        response.put("timestamp", LocalDateTime.now());
        response.put("features", Arrays.asList(
            "time-sync",
            "data-fusion", 
            "feature-extraction",
            "preprocessing",
            "knowledge-graph",
            "maintenance-extract"
        ));
        return response;
    }
}
