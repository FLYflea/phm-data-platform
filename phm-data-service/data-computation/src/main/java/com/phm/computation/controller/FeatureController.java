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
            List<Double> doubleValues = parseValues(request);
            if (doubleValues == null) {
                return ResponseEntity.badRequest().body(buildErrorResponse("数值列表不能为空"));
            }
            
            Map<String, Double> features = featureEngineeringService.extractTimeDomain(doubleValues);
            
            Map<String, Object> data = new HashMap<>();
            data.put("features", features);
            data.put("featureCount", features.size());
            
            return ResponseEntity.ok(buildSuccessResponse(data, "时域特征提取完成"));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(buildErrorResponse("特征提取失败: " + e.getMessage()));
        }
    }

    @PostMapping("/frequency-domain")
    public ResponseEntity<Map<String, Object>> extractFrequencyDomain(@RequestBody Map<String, Object> request) {
        try {
            List<Double> doubleValues = parseValues(request);
            if (doubleValues == null) {
                return ResponseEntity.badRequest().body(buildErrorResponse("数值列表不能为空"));
            }

            Map<String, Object> features = featureEngineeringService.extractFrequencyDomain(doubleValues);

            Map<String, Object> data = new HashMap<>();
            data.put("features", features);
            data.put("featureCount", features.size());

            return ResponseEntity.ok(buildSuccessResponse(data, "频域特征提取完成"));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(buildErrorResponse("频域特征提取失败: " + e.getMessage()));
        }
    }

    @PostMapping("/time-frequency")
    public ResponseEntity<Map<String, Object>> extractTimeFrequency(@RequestBody Map<String, Object> request) {
        try {
            List<Double> doubleValues = parseValues(request);
            if (doubleValues == null) {
                return ResponseEntity.badRequest().body(buildErrorResponse("数值列表不能为空"));
            }

            Map<String, Object> features = featureEngineeringService.extractTimeFrequency(doubleValues);

            Map<String, Object> data = new HashMap<>();
            data.put("features", features);
            data.put("featureCount", features.size());

            return ResponseEntity.ok(buildSuccessResponse(data, "时频域特征提取完成（Haar小波变换）"));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(buildErrorResponse("时频域特征提取失败: " + e.getMessage()));
        }
    }

    @PostMapping("/all")
    public ResponseEntity<Map<String, Object>> extractAllFeatures(@RequestBody Map<String, Object> request) {
        try {
            List<Double> doubleValues = parseValues(request);
            if (doubleValues == null) {
                return ResponseEntity.badRequest().body(buildErrorResponse("数值列表不能为空"));
            }

            Map<String, Object> allFeatures = featureEngineeringService.extractAllFeatures(doubleValues);

            return ResponseEntity.ok(buildSuccessResponse(allFeatures, "全部特征提取完成（时域+频域+时频域）"));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(buildErrorResponse("全部特征提取失败: " + e.getMessage()));
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<Double> parseValues(Map<String, Object> request) {
        List<Number> values = (List<Number>) request.get("values");
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream().map(Number::doubleValue).toList();
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
