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
import java.util.List;

/**
 * 统一查询控制器
 * 
 * 职责：封装底层存储接口，提供统一的查询入口
 * - 接收前端查询请求
 * - 调用 data-storage 服务 (8103)
 * - 返回标准化结果
 */
@Slf4j
@RestController
@RequestMapping("/service")
@RequiredArgsConstructor
public class UnifiedQueryController {
    
    private final RestTemplate restTemplate;
    
    @Value("${storage.service.url}")
    private String storageServiceUrl;
    
    /**
     * 统一查询接口
     * 调用路径：POST /service/query
     * 转发到：GET http://localhost:8103/storage/timeseries/query
     * 
     * @param request 查询请求参数
     * @return 查询结果
     */
    @PostMapping("/query")
    public ResponseEntity<?> unifiedQuery(@RequestBody QueryRequest request) {
        log.info("统一查询请求: deviceId={}, startTime={}, endTime={}", 
                request.getDeviceId(), request.getStartTime(), request.getEndTime());
        
        // 构建存储层查询URL
        String queryUrl = UriComponentsBuilder
                .fromHttpUrl(storageServiceUrl + "/storage/timeseries/query")
                .queryParam("deviceId", request.getDeviceId())
                .queryParam("start", request.getStartTime().toString())
                .queryParam("end", request.getEndTime().toString())
                .toUriString();
        
        log.info("转发到存储层: {}", queryUrl);
        
        try {
            // 调用存储层服务
            ResponseEntity<List> response = restTemplate.getForEntity(queryUrl, List.class);
            
            log.info("查询成功，返回数据条数: {}", response.getBody() != null ? response.getBody().size() : 0);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.error("查询失败: {}", e.getMessage());
            return ResponseEntity.status(500).body("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询请求参数
     */
    @Data
    public static class QueryRequest {
        private String deviceId;
        private Instant startTime;
        private Instant endTime;
    }
}
