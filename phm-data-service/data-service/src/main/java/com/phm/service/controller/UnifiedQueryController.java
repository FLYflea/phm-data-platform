package com.phm.service.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一查询控制器（已改造）
 * 
 * 职责：
 * - queryRawData() → 调 storage (8103) 查询原始数据
 * - queryProcessedData() → 调 computation (8102) 查询处理后数据
 * - 只做路由选择，不做数据组装
 */
@Slf4j
@RestController
@RequestMapping("/service")
@RequiredArgsConstructor
public class UnifiedQueryController {
    
    private final RestTemplate restTemplate;
    
    @Value("${storage.service.url:http://localhost:8103}")
    private String storageServiceUrl;
    
    @Value("${computation.service.url:http://localhost:8102}")
    private String computationServiceUrl;
    
    /**
     * 查询原始数据
     * 调用路径：POST /service/query/raw
     * 转发到：storage:8103/storage/timeseries/query
     * 
     * @param request 查询请求参数
     * @return 原始数据列表
     */
    @PostMapping("/query/raw")
    public ResponseEntity<?> queryRawData(@RequestBody QueryRequest request) {
        log.info("查询原始数据: deviceId={}, startTime={}, endTime={}", 
                request.getDeviceId(), request.getStartTime(), request.getEndTime());
        
        String queryUrl = UriComponentsBuilder
                .fromHttpUrl(storageServiceUrl + "/storage/timeseries/query")
                .queryParam("deviceId", request.getDeviceId())
                .queryParam("start", request.getStartTime())
                .queryParam("end", request.getEndTime())
                .toUriString();
        
        log.info("转发到存储层: {}", queryUrl);
        
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(queryUrl, Map.class);
            
            Map<String, Object> result = new HashMap<>();
            result.put("source", "storage");
            result.put("dataType", "raw");
            result.put("data", response.getBody());
            
            log.info("原始数据查询成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("原始数据查询失败: {}", e.getMessage());
            return ResponseEntity.status(500).body(buildErrorResponse("查询失败: " + e.getMessage()));
        }
    }
    
    /**
     * 查询处理后的数据（带特征）
     * 调用路径：POST /service/query/processed
     * 转发到：computation:8102/computation/feature/summary
     * 
     * @param request 查询请求参数
     * @return 处理后数据（含特征）
     */
    @PostMapping("/query/processed")
    public ResponseEntity<?> queryProcessedData(@RequestBody QueryRequest request) {
        log.info("查询处理后数据: deviceId={}, startTime={}, endTime={}", 
                request.getDeviceId(), request.getStartTime(), request.getEndTime());
        
        // 先从 storage 获取原始数据
        String queryUrl = UriComponentsBuilder
                .fromHttpUrl(storageServiceUrl + "/storage/timeseries/query")
                .queryParam("deviceId", request.getDeviceId())
                .queryParam("start", request.getStartTime())
                .queryParam("end", request.getEndTime())
                .toUriString();
        
        try {
            // 获取原始数据
            @SuppressWarnings("unchecked")
            Map<String, Object> storageResponse = restTemplate.getForObject(queryUrl, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawData = storageResponse != null ? 
                (List<Map<String, Object>>) storageResponse.get("data") : new ArrayList<>();
            
            if (rawData.isEmpty()) {
                Map<String, Object> emptyResult = new HashMap<>();
                emptyResult.put("source", "computation");
                emptyResult.put("dataType", "processed");
                emptyResult.put("data", new ArrayList<>());
                emptyResult.put("features", new HashMap<>());
                return ResponseEntity.ok(emptyResult);
            }
            
            // 提取数值调用计算层特征提取
            List<Double> values = rawData.stream()
                .map(d -> ((Number) d.get("value")).doubleValue())
                .toList();
            
            String featureUrl = computationServiceUrl + "/computation/feature/summary";
            Map<String, Object> featureRequest = new HashMap<>();
            featureRequest.put("values", values);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> featureResponse = restTemplate.postForObject(featureUrl, featureRequest, Map.class);
            
            Map<String, Object> result = new HashMap<>();
            result.put("source", "computation");
            result.put("dataType", "processed");
            result.put("rawData", rawData);
            result.put("features", featureResponse != null ? featureResponse.get("data") : new HashMap<>());
            
            log.info("处理后数据查询成功，数据条数: {}", rawData.size());
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("处理后数据查询失败: {}", e.getMessage());
            return ResponseEntity.status(500).body(buildErrorResponse("查询失败: " + e.getMessage()));
        }
    }
    
    private Map<String, Object> buildErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        return response;
    }
    
    /**
     * 查询请求参数
     */
    @Data
    public static class QueryRequest {
        private String deviceId;
        private String startTime;
        private String endTime;
    }
}
