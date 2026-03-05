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
import java.util.List;
import java.util.Map;

/**
 * 可视化数据控制器
 * 
 * 职责：将原始数据转换为图表所需的格式
 * - 时间序列数据 → 图表格式 { timestamps: [...], values: [...] }
 */
@Slf4j
@RestController
@RequestMapping("/service")
@RequiredArgsConstructor
public class VisualizationController {
    
    private final RestTemplate restTemplate;
    
    @Value("${storage.service.url}")
    private String storageServiceUrl;
    
    /**
     * 获取图表数据
     * 返回格式：{ "timestamps": [...], "values": [...] }
     * 
     * @param deviceId 设备ID
     * @return 图表数据
     */
    @GetMapping("/chart")
    public ResponseEntity<?> getChartData(@RequestParam String deviceId) {
        log.info("获取图表数据: deviceId={}", deviceId);
        
        // 默认查询最近24小时数据
        Instant endTime = Instant.now();
        Instant startTime = endTime.minusSeconds(24 * 60 * 60); // 24小时前
        
        // 构建存储层查询URL
        String queryUrl = UriComponentsBuilder
                .fromHttpUrl(storageServiceUrl + "/storage/timeseries/query")
                .queryParam("deviceId", deviceId)
                .queryParam("start", startTime.toString())
                .queryParam("end", endTime.toString())
                .toUriString();
        
        try {
            // 调用存储层服务获取原始数据
            ResponseEntity<List> response = restTemplate.getForEntity(queryUrl, List.class);
            List<Map<String, Object>> rawData = response.getBody();
            
            // 转换为图表格式
            ChartData chartData = convertToChartData(rawData);
            
            log.info("图表数据生成完成: timestamps={}, values={}", 
                    chartData.getTimestamps().size(), chartData.getValues().size());
            
            return ResponseEntity.ok(chartData);
        } catch (Exception e) {
            log.error("获取图表数据失败: {}", e.getMessage());
            return ResponseEntity.status(500).body("获取图表数据失败: " + e.getMessage());
        }
    }
    
    /**
     * 将原始数据转换为图表格式
     * 
     * @param rawData 原始传感器数据列表
     * @return 图表数据
     */
    private ChartData convertToChartData(List<Map<String, Object>> rawData) {
        ChartData chartData = new ChartData();
        List<String> timestamps = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        
        if (rawData != null) {
            for (Map<String, Object> record : rawData) {
                // 提取时间戳
                Object timestampObj = record.get("timestamp");
                if (timestampObj != null) {
                    timestamps.add(timestampObj.toString());
                }
                
                // 提取数值
                Object valueObj = record.get("value");
                if (valueObj != null) {
                    values.add(Double.valueOf(valueObj.toString()));
                }
            }
        }
        
        chartData.setTimestamps(timestamps);
        chartData.setValues(values);
        return chartData;
    }
    
    /**
     * 图表数据格式
     */
    @Data
    public static class ChartData {
        private List<String> timestamps;
        private List<Double> values;
    }
}
