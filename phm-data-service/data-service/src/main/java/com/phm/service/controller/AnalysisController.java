package com.phm.service.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

/**
 * P1: 多维分析控制器（简化版）
 *
 * 职责：数据统计分析
 * - 调用 storage 聚合接口
 * - 返回统计指标
 */
@Slf4j
@RestController
@RequestMapping("/service/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final RestTemplate restTemplate;

    @Value("${storage.service.url}")
    private String storageServiceUrl;

    /**
     * P1: 获取统计指标
     *
     * @param deviceId 设备ID
     * @param sensorType 传感器类型
     * @param start 开始时间
     * @param end 结束时间
     * @return 统计指标 { avg, max, min, count }
     */
    @GetMapping("/statistics")
    public Map<String, Object> getStatistics(
            @RequestParam String deviceId,
            @RequestParam String sensorType,
            @RequestParam String start,
            @RequestParam String end) {

        log.info("P1获取统计指标: deviceId={}, sensorType={}", deviceId, sensorType);

        // 调用 storage 聚合接口
        String url = UriComponentsBuilder
                .fromHttpUrl(storageServiceUrl + "/storage/timeseries/aggregate")
                .queryParam("deviceId", deviceId)
                .queryParam("start", start)
                .queryParam("end", end)
                .queryParam("interval", "hour")
                .toUriString();

        Map<String, Object> response = new HashMap<>();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> aggregations = result != null ?
                    (List<Map<String, Object>>) result.get("aggregations") : Collections.emptyList();

            // 计算整体统计
            double sum = 0;
            double max = Double.MIN_VALUE;
            double min = Double.MAX_VALUE;
            long totalCount = 0;

            for (Map<String, Object> agg : aggregations) {
                Object avgObj = agg.get("avgValue");
                Object maxObj = agg.get("maxValue");
                Object minObj = agg.get("minValue");
                Object countObj = agg.get("count");

                if (avgObj != null) {
                    sum += Double.parseDouble(avgObj.toString());
                }
                if (maxObj != null) {
                    max = Math.max(max, Double.parseDouble(maxObj.toString()));
                }
                if (minObj != null) {
                    min = Math.min(min, Double.parseDouble(minObj.toString()));
                }
                if (countObj != null) {
                    totalCount += Long.parseLong(countObj.toString());
                }
            }

            double avg = aggregations.isEmpty() ? 0 : sum / aggregations.size();

            response.put("avg", Math.round(avg * 100.0) / 100.0);
            response.put("max", max == Double.MIN_VALUE ? 0 : Math.round(max * 100.0) / 100.0);
            response.put("min", min == Double.MAX_VALUE ? 0 : Math.round(min * 100.0) / 100.0);
            response.put("count", totalCount);
            response.put("hourCount", aggregations.size());
            response.put("status", "success");

            log.info("P1统计指标完成: avg={}, max={}, min={}, count={}",
                    avg, max, min, totalCount);

        } catch (Exception e) {
            log.error("P1获取统计指标失败: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", e.getMessage());
        }

        return response;
    }
}
