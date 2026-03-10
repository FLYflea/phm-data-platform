package com.phm.service.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * P1: 可视化数据控制器
 *
 * 职责：将原始数据转换为 ECharts 图表格式
 * - 时序数据 → ECharts line 图表
 * - 饼图数据（预留）
 */
@Slf4j
@RestController
@RequestMapping("/service/chart")
@RequiredArgsConstructor
public class VisualizationController {

    private final RestTemplate restTemplate;

    @Value("${storage.service.url}")
    private String storageServiceUrl;

    /**
     * P1: 获取时序数据图表（ECharts格式）
     *
     * @param deviceId 设备ID
     * @param sensorType 传感器类型
     * @param start 开始时间
     * @param end 结束时间
     * @return ECharts格式数据 { xAxis: [...], series: [...] }
     */
    @GetMapping("/timeseries")
    public Map<String, Object> getTimeseriesChart(
            @RequestParam(name = "deviceId") String deviceId,
            @RequestParam(name = "sensorType") String sensorType,
            @RequestParam(name = "start") String start,
            @RequestParam(name = "end") String end) {

        log.info("P1获取时序图表: deviceId={}, sensorType={}", deviceId, sensorType);

        // 查询 storage 层
        String url = UriComponentsBuilder
                .fromHttpUrl(storageServiceUrl + "/storage/timeseries/query")
                .queryParam("deviceId", deviceId)
                .queryParam("sensorType", sensorType)
                .queryParam("start", start)
                .queryParam("end", end)
                .toUriString();

        Map<String, Object> response = new HashMap<>();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = result != null ? (List<Map<String, Object>>) result.get("data") : Collections.emptyList();

            // 转换为 ECharts 格式
            List<String> xAxis = new ArrayList<>();
            List<Double> seriesData = new ArrayList<>();

            for (Map<String, Object> item : data) {
                Object timestamp = item.get("timestamp");
                Object value = item.get("value");

                if (timestamp != null) {
                    xAxis.add(timestamp.toString());
                }
                if (value != null) {
                    seriesData.add(Double.valueOf(value.toString()));
                }
            }

            // 构建 ECharts 格式
            Map<String, Object> series = new HashMap<>();
            series.put("name", sensorType);
            series.put("type", "line");
            series.put("data", seriesData);
            series.put("smooth", true);

            response.put("xAxis", xAxis);
            response.put("series", Collections.singletonList(series));
            response.put("status", "success");
            response.put("count", data.size());

            log.info("P1时序图表生成完成: {} 个数据点", data.size());

        } catch (Exception e) {
            log.error("P1获取时序图表失败: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", e.getMessage());
        }

        return response;
    }

    /**
     * P1: 饼图接口（预留）
     *
     * @return 预留提示
     */
    @GetMapping("/pie")
    public Map<String, Object> getPieChart() {
        log.info("P1饼图接口调用（预留）");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "reserved");
        response.put("note", "饼图待实现");
        response.put("plannedFeatures", Arrays.asList(
            "设备状态分布",
            "传感器类型分布",
            "故障类型分布",
            "告警级别分布"
        ));

        return response;
    }
}
