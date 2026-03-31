package com.phm.service.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

/**
 * 数据流计算服务控制器
 *
 * 职责：实时数据流推送服务
 * - SSE (Server-Sent Events) 实时数据推送
 * - 数据流订阅管理
 * - 流数据窗口计算（滑动窗口统计）
 * - 实时告警推送
 */
@Slf4j
@RestController
@RequestMapping("/service/stream")
@RequiredArgsConstructor
public class StreamController {

    private final RestTemplate restTemplate;

    @Value("${storage.service.url:http://localhost:8103}")
    private String storageServiceUrl;

    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /**
     * SSE 实时数据流订阅
     * 客户端通过 EventSource 连接，服务端定时推送最新数据
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @RequestParam(name = "deviceId", defaultValue = "EQ-001") String deviceId,
            @RequestParam(name = "sensorType", defaultValue = "temperature") String sensorType,
            @RequestParam(name = "interval", defaultValue = "3000") long interval) {

        log.info("SSE数据流订阅: deviceId={}, sensorType={}, interval={}ms", deviceId, sensorType, interval);

        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时
        String emitterId = deviceId + "_" + sensorType + "_" + System.currentTimeMillis();

        activeEmitters.put(emitterId, emitter);

        // 定时推送数据
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            try {
                Map<String, Object> data = fetchLatestData(deviceId, sensorType);
                emitter.send(SseEmitter.event()
                        .name("data")
                        .data(data));
            } catch (IOException e) {
                log.warn("SSE发送失败: {}", e.getMessage());
                activeEmitters.remove(emitterId);
                emitter.complete();
            } catch (Exception e) {
                log.error("数据获取失败: {}", e.getMessage());
            }
        }, 0, interval, TimeUnit.MILLISECONDS);

        // 连接关闭时清理
        emitter.onCompletion(() -> {
            log.info("SSE连接关闭: {}", emitterId);
            task.cancel(true);
            activeEmitters.remove(emitterId);
        });
        emitter.onTimeout(() -> {
            log.info("SSE连接超时: {}", emitterId);
            task.cancel(true);
            activeEmitters.remove(emitterId);
            emitter.complete();
        });
        emitter.onError(t -> {
            log.warn("SSE连接错误: {}", t.getMessage());
            task.cancel(true);
            activeEmitters.remove(emitterId);
        });

        return emitter;
    }

    /**
     * 查询当前活跃的数据流连接
     */
    @GetMapping("/connections")
    public Map<String, Object> getConnections() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("activeCount", activeEmitters.size());
        response.put("connections", new ArrayList<>(activeEmitters.keySet()));
        return response;
    }

    /**
     * 获取流窗口统计（最近N分钟的滑动窗口统计）
     */
    @GetMapping("/window-stats")
    public Map<String, Object> getWindowStats(
            @RequestParam(name = "deviceId", defaultValue = "EQ-001") String deviceId,
            @RequestParam(name = "sensorType", defaultValue = "temperature") String sensorType,
            @RequestParam(name = "windowMinutes", defaultValue = "5") int windowMinutes) {

        log.info("流窗口统计: deviceId={}, sensorType={}, window={}min", deviceId, sensorType, windowMinutes);

        String start = Instant.now().minus(windowMinutes, ChronoUnit.MINUTES).toString();
        String end = Instant.now().toString();

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
            List<Map<String, Object>> data = result != null ?
                    (List<Map<String, Object>>) result.get("data") : Collections.emptyList();

            double sum = 0, max = Double.MIN_VALUE, min = Double.MAX_VALUE;
            int count = 0;
            String latestTimestamp = null;
            double latestValue = 0;

            for (Map<String, Object> item : data) {
                Object valObj = item.get("value");
                if (valObj != null) {
                    double v = Double.parseDouble(valObj.toString());
                    sum += v;
                    max = Math.max(max, v);
                    min = Math.min(min, v);
                    count++;
                    latestTimestamp = item.get("timestamp") != null ? item.get("timestamp").toString() : null;
                    latestValue = v;
                }
            }

            response.put("status", "success");
            response.put("windowMinutes", windowMinutes);
            response.put("count", count);
            response.put("avg", count > 0 ? Math.round(sum / count * 100.0) / 100.0 : 0);
            response.put("max", max == Double.MIN_VALUE ? 0 : Math.round(max * 100.0) / 100.0);
            response.put("min", min == Double.MAX_VALUE ? 0 : Math.round(min * 100.0) / 100.0);
            response.put("latestValue", Math.round(latestValue * 100.0) / 100.0);
            response.put("latestTimestamp", latestTimestamp);

        } catch (Exception e) {
            log.error("流窗口统计失败: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", e.getMessage());
        }

        return response;
    }

    /**
     * 获取最新数据（供SSE推送使用）
     */
    private Map<String, Object> fetchLatestData(String deviceId, String sensorType) {
        String start = Instant.now().minus(1, ChronoUnit.MINUTES).toString();
        String end = Instant.now().toString();

        String url = UriComponentsBuilder
                .fromHttpUrl(storageServiceUrl + "/storage/timeseries/query")
                .queryParam("deviceId", deviceId)
                .queryParam("sensorType", sensorType)
                .queryParam("start", start)
                .queryParam("end", end)
                .toUriString();

        Map<String, Object> payload = new HashMap<>();
        payload.put("deviceId", deviceId);
        payload.put("sensorType", sensorType);
        payload.put("timestamp", Instant.now().toString());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = result != null ?
                    (List<Map<String, Object>>) result.get("data") : Collections.emptyList();

            if (!data.isEmpty()) {
                Map<String, Object> latest = data.get(data.size() - 1);
                payload.put("value", latest.get("value"));
                payload.put("dataTimestamp", latest.get("timestamp"));
                payload.put("count", data.size());
            } else {
                payload.put("value", null);
                payload.put("count", 0);
            }
        } catch (Exception e) {
            payload.put("error", e.getMessage());
        }

        return payload;
    }
}
