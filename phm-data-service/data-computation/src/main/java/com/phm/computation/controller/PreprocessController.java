package com.phm.computation.controller;

import com.phm.computation.entity.SensorData;
import com.phm.computation.service.PreprocessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据预处理控制器
 */
@Slf4j
@RestController
@RequestMapping("/computation/preprocess")
@RequiredArgsConstructor
public class PreprocessController {

    private final PreprocessService preprocessService;

    @PostMapping("/denoise")
    public ResponseEntity<Map<String, Object>> denoise(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Number> values = (List<Number>) request.get("values");
            
            if (values == null || values.isEmpty()) {
                return ResponseEntity.badRequest().body(buildErrorResponse("数值列表不能为空"));
            }
            
            List<Double> doubleValues = values.stream()
                    .map(Number::doubleValue)
                    .collect(Collectors.toList());
            
            List<Double> denoised = preprocessService.denoise(doubleValues);
            
            Map<String, Object> data = new HashMap<>();
            data.put("original", doubleValues);
            data.put("denoised", denoised);
            
            return ResponseEntity.ok(buildSuccessResponse(data, "去噪完成"));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(buildErrorResponse("去噪失败: " + e.getMessage()));
        }
    }
    
    @PostMapping("/normalize")
    public ResponseEntity<Map<String, Object>> normalize(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Number> values = (List<Number>) request.get("values");
            
            if (values == null || values.isEmpty()) {
                return ResponseEntity.badRequest().body(buildErrorResponse("数值列表不能为空"));
            }
            
            List<Double> doubleValues = values.stream()
                    .map(Number::doubleValue)
                    .collect(Collectors.toList());
            
            List<Double> normalized = preprocessService.normalize(doubleValues);
            
            Map<String, Object> data = new HashMap<>();
            data.put("original", doubleValues);
            data.put("normalized", normalized);
            
            return ResponseEntity.ok(buildSuccessResponse(data, "标准化完成"));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(buildErrorResponse("标准化失败: " + e.getMessage()));
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
