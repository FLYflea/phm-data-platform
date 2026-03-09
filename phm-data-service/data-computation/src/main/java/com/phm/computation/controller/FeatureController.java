package com.phm.computation.controller;

import com.phm.computation.service.FeatureEngineeringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 特征工程控制器
 */
@Slf4j
@RestController
@RequestMapping("/computation/feature")
@RequiredArgsConstructor
public class FeatureController {

    private final FeatureEngineeringService featureEngineeringService;

    @PostMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Number> values = (List<Number>) request.get("values");
            
            if (values == null || values.isEmpty()) {
                return ResponseEntity.badRequest().body(buildErrorResponse("数值列表不能为空"));
            }
            
            List<Double> doubleValues = values.stream()
                    .map(Number::doubleValue)
                    .toList();
            
            double max = doubleValues.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            double min = doubleValues.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            double mean = doubleValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            
            Map<String, Object> summary = new HashMap<>();
            summary.put("max", max);
            summary.put("min", min);
            summary.put("mean", mean);
            summary.put("count", doubleValues.size());
            
            return ResponseEntity.ok(buildSuccessResponse(summary, "统计摘要计算完成"));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(buildErrorResponse("计算失败: " + e.getMessage()));
        }
    }
    
    @PostMapping("/time-domain")
    public ResponseEntity<Map<String, Object>> extractTimeDomain(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Number> values = (List<Number>) request.get("values");
            
            if (values == null || values.isEmpty()) {
                return ResponseEntity.badRequest().body(buildErrorResponse("数值列表不能为空"));
            }
            
            List<Double> doubleValues = values.stream()
                    .map(Number::doubleValue)
                    .toList();
            
            Map<String, Double> features = featureEngineeringService.extractTimeDomain(doubleValues);
            
            Map<String, Object> data = new HashMap<>();
            data.put("features", features);
            data.put("featureCount", features.size());
            
            return ResponseEntity.ok(buildSuccessResponse(data, "时域特征提取完成"));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(buildErrorResponse("特征提取失败: " + e.getMessage()));
        }
    }
    
    private Map<String, Object> buildSuccessResponse(Object data, String note) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", data);
        response.put("note", note);
        return response;
    }
    
    private Map<String, Object> buildErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        return response;
    }
}
