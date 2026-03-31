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
 * 数据可视化控制器
 *
 * 职责：将原始数据转换为 ECharts 图表格式
 * 支持图表类型：折线图、柱状图、散点图、饼图、雷达图、热力图
 * 基于图形语法构建可视化模板，SVG/Canvas 渲染，HTML5 兼容
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
     * 获取时序数据图表（ECharts格式）
     * 支持：折线图、柱状图、散点图、面积图
     */
    @GetMapping("/timeseries")
    public Map<String, Object> getTimeseriesChart(
            @RequestParam(name = "deviceId") String deviceId,
            @RequestParam(name = "sensorType") String sensorType,
            @RequestParam(name = "start") String start,
            @RequestParam(name = "end") String end) {

        log.info("获取时序图表: deviceId={}, sensorType={}", deviceId, sensorType);

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

            Map<String, Object> series = new HashMap<>();
            series.put("name", sensorType);
            series.put("type", "line");
            series.put("data", seriesData);
            series.put("smooth", true);

            response.put("xAxis", xAxis);
            response.put("series", Collections.singletonList(series));
            response.put("status", "success");
            response.put("count", data.size());

            log.info("时序图表生成完成: {} 个数据点", data.size());

        } catch (Exception e) {
            log.error("获取时序图表失败: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", e.getMessage());
        }

        return response;
    }

    /**
     * 获取饼图数据 —— 传感器类型分布
     * 统计指定设备各传感器类型的数据量占比
     */
    @GetMapping("/pie")
    public Map<String, Object> getPieChart(
            @RequestParam(name = "deviceId", defaultValue = "EQ-001") String deviceId,
            @RequestParam(name = "start", required = false) String start,
            @RequestParam(name = "end", required = false) String end) {

        log.info("获取饼图: deviceId={}", deviceId);

        if (start == null) {
            start = Instant.now().minus(7, ChronoUnit.DAYS).toString();
        }
        if (end == null) {
            end = Instant.now().toString();
        }

        Map<String, Object> response = new HashMap<>();
        String[] sensorTypes = {"temperature", "vibration", "pressure", "current"};
        String[] sensorLabels = {"温度", "振动", "压力", "电流"};
        List<Map<String, Object>> pieData = new ArrayList<>();

        try {
            for (int i = 0; i < sensorTypes.length; i++) {
                String url = UriComponentsBuilder
                        .fromHttpUrl(storageServiceUrl + "/storage/timeseries/query")
                        .queryParam("deviceId", deviceId)
                        .queryParam("sensorType", sensorTypes[i])
                        .queryParam("start", start)
                        .queryParam("end", end)
                        .toUriString();

                @SuppressWarnings("unchecked")
                Map<String, Object> result = restTemplate.getForObject(url, Map.class);
                @SuppressWarnings("unchecked")
                List<?> data = result != null ? (List<?>) result.get("data") : Collections.emptyList();

                Map<String, Object> item = new HashMap<>();
                item.put("name", sensorLabels[i]);
                item.put("value", data != null ? data.size() : 0);
                item.put("sensorType", sensorTypes[i]);
                pieData.add(item);
            }

            response.put("data", pieData);
            response.put("status", "success");
            response.put("chartType", "pie");
            log.info("饼图数据生成完成: {} 类传感器", pieData.size());

        } catch (Exception e) {
            log.error("获取饼图失败: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", e.getMessage());
        }

        return response;
    }

    /**
     * 获取雷达图数据 —— 多维特征对比
     * 对比多个传感器的统计指标（均值、最大值、最小值、标准差、数据量）
     */
    @GetMapping("/radar")
    public Map<String, Object> getRadarChart(
            @RequestParam(name = "deviceId", defaultValue = "EQ-001") String deviceId,
            @RequestParam(name = "start", required = false) String start,
            @RequestParam(name = "end", required = false) String end) {

        log.info("获取雷达图: deviceId={}", deviceId);

        if (start == null) {
            start = Instant.now().minus(7, ChronoUnit.DAYS).toString();
        }
        if (end == null) {
            end = Instant.now().toString();
        }

        Map<String, Object> response = new HashMap<>();
        String[] sensorTypes = {"temperature", "vibration", "pressure", "current"};
        String[] sensorLabels = {"温度", "振动", "压力", "电流"};

        // 雷达图指标维度
        List<Map<String, Object>> indicators = new ArrayList<>();
        String[] dims = {"均值", "最大值", "数据量", "波动度"};

        try {
            List<Map<String, Object>> seriesData = new ArrayList<>();
            double[] maxValues = new double[dims.length];
            Arrays.fill(maxValues, 1);

            // 先收集所有传感器数据
            List<double[]> allValues = new ArrayList<>();
            for (int i = 0; i < sensorTypes.length; i++) {
                String url = UriComponentsBuilder
                        .fromHttpUrl(storageServiceUrl + "/storage/timeseries/query")
                        .queryParam("deviceId", deviceId)
                        .queryParam("sensorType", sensorTypes[i])
                        .queryParam("start", start)
                        .queryParam("end", end)
                        .toUriString();

                @SuppressWarnings("unchecked")
                Map<String, Object> result = restTemplate.getForObject(url, Map.class);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = result != null ?
                        (List<Map<String, Object>>) result.get("data") : Collections.emptyList();

                double sum = 0, max = 0, count = data.size();
                double sumSq = 0;
                for (Map<String, Object> item : data) {
                    Object valueObj = item.get("value");
                    if (valueObj != null) {
                        double v = Double.parseDouble(valueObj.toString());
                        sum += v;
                        max = Math.max(max, Math.abs(v));
                        sumSq += v * v;
                    }
                }
                double mean = count > 0 ? sum / count : 0;
                double variance = count > 0 ? sumSq / count - mean * mean : 0;
                double std = Math.sqrt(Math.max(0, variance));

                double[] vals = {
                    Math.round(mean * 100.0) / 100.0,
                    Math.round(max * 100.0) / 100.0,
                    count,
                    Math.round(std * 100.0) / 100.0
                };
                allValues.add(vals);

                for (int j = 0; j < dims.length; j++) {
                    maxValues[j] = Math.max(maxValues[j], vals[j]);
                }
            }

            // 构建指标
            for (int j = 0; j < dims.length; j++) {
                Map<String, Object> ind = new HashMap<>();
                ind.put("name", dims[j]);
                ind.put("max", Math.ceil(maxValues[j] * 1.2));
                indicators.add(ind);
            }

            // 构建系列数据
            for (int i = 0; i < sensorTypes.length; i++) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", sensorLabels[i]);
                item.put("value", allValues.get(i));
                seriesData.add(item);
            }

            response.put("indicators", indicators);
            response.put("data", seriesData);
            response.put("status", "success");
            response.put("chartType", "radar");
            log.info("雷达图数据生成完成");

        } catch (Exception e) {
            log.error("获取雷达图失败: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", e.getMessage());
        }

        return response;
    }

    /**
     * 获取热力图数据 —— 按小时聚合的数值热力分布
     * X轴为日期，Y轴为小时（0-23），值为该时段的平均值
     */
    @GetMapping("/heatmap")
    public Map<String, Object> getHeatmapChart(
            @RequestParam(name = "deviceId", defaultValue = "EQ-001") String deviceId,
            @RequestParam(name = "sensorType", defaultValue = "temperature") String sensorType,
            @RequestParam(name = "start", required = false) String start,
            @RequestParam(name = "end", required = false) String end) {

        log.info("获取热力图: deviceId={}, sensorType={}", deviceId, sensorType);

        if (start == null) {
            start = Instant.now().minus(7, ChronoUnit.DAYS).toString();
        }
        if (end == null) {
            end = Instant.now().toString();
        }

        Map<String, Object> response = new HashMap<>();

        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(storageServiceUrl + "/storage/timeseries/query")
                    .queryParam("deviceId", deviceId)
                    .queryParam("sensorType", sensorType)
                    .queryParam("start", start)
                    .queryParam("end", end)
                    .toUriString();

            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = result != null ?
                    (List<Map<String, Object>>) result.get("data") : Collections.emptyList();

            // 按日期+小时聚合
            Map<String, Map<Integer, List<Double>>> dateHourMap = new TreeMap<>();
            for (Map<String, Object> item : data) {
                Object tsObj = item.get("timestamp");
                Object valObj = item.get("value");
                if (tsObj == null || valObj == null) continue;

                Instant ts = Instant.parse(tsObj.toString());
                java.time.ZonedDateTime zdt = ts.atZone(java.time.ZoneId.systemDefault());
                String dateStr = zdt.toLocalDate().toString();
                int hour = zdt.getHour();
                double value = Double.parseDouble(valObj.toString());

                dateHourMap
                    .computeIfAbsent(dateStr, k -> new HashMap<>())
                    .computeIfAbsent(hour, k -> new ArrayList<>())
                    .add(value);
            }

            // 构建热力图数据 [dayIndex, hour, avgValue]
            List<String> dates = new ArrayList<>(dateHourMap.keySet());
            List<List<Object>> heatmapData = new ArrayList<>();
            double maxVal = 0;

            for (int dayIdx = 0; dayIdx < dates.size(); dayIdx++) {
                Map<Integer, List<Double>> hourMap = dateHourMap.get(dates.get(dayIdx));
                for (Map.Entry<Integer, List<Double>> entry : hourMap.entrySet()) {
                    int hour = entry.getKey();
                    List<Double> vals = entry.getValue();
                    double avg = vals.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    avg = Math.round(avg * 100.0) / 100.0;
                    maxVal = Math.max(maxVal, avg);
                    heatmapData.add(Arrays.asList(dayIdx, hour, avg));
                }
            }

            List<String> hours = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                hours.add(i + ":00");
            }

            response.put("xAxis", dates);
            response.put("yAxis", hours);
            response.put("data", heatmapData);
            response.put("maxValue", maxVal);
            response.put("status", "success");
            response.put("chartType", "heatmap");
            log.info("热力图数据生成完成: {} 天, {} 个数据点", dates.size(), heatmapData.size());

        } catch (Exception e) {
            log.error("获取热力图失败: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", e.getMessage());
        }

        return response;
    }
}
