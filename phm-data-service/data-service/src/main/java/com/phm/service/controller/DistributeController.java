package com.phm.service.controller;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 数据分发控制器
 *
 * 职责：基于 Lazy-Automata 模式的主动数据分发机制
 * - 订阅者注册管理
 * - 数据变更监听
 * - 自动推送机制（模拟）
 * - 分发策略配置
 */
@Slf4j
@RestController
@RequestMapping("/service/distribute")
public class DistributeController {

    private final AtomicLong idGenerator = new AtomicLong(1);
    private final Map<Long, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> eventLog = Collections.synchronizedList(new ArrayList<>());

    /**
     * 注册分发订阅
     */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody SubscriptionRequest request) {
        log.info("注册订阅: deviceId={}, sensorType={}, callback={}", 
                request.getDeviceId(), request.getSensorType(), request.getCallbackUrl());

        long id = idGenerator.getAndIncrement();
        Subscription sub = new Subscription();
        sub.setId(id);
        sub.setDeviceId(request.getDeviceId());
        sub.setSensorType(request.getSensorType());
        sub.setCallbackUrl(request.getCallbackUrl());
        sub.setStrategy(request.getStrategy() != null ? request.getStrategy() : "on_change");
        sub.setStatus("active");
        sub.setCreatedAt(Instant.now().toString());
        sub.setPushCount(0);
        sub.setLastPushAt(null);

        subscriptions.put(id, sub);

        // 记录事件
        logEvent("REGISTER", "订阅注册成功: ID=" + id + ", 设备=" + request.getDeviceId());

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("subscriptionId", id);
        response.put("message", "订阅注册成功");
        response.put("subscription", sub);
        return response;
    }

    /**
     * 取消订阅
     */
    @DeleteMapping("/unregister/{id}")
    public Map<String, Object> unregister(@PathVariable("id") Long id) {
        log.info("取消订阅: id={}", id);

        Map<String, Object> response = new HashMap<>();
        Subscription sub = subscriptions.remove(id);
        if (sub != null) {
            logEvent("UNREGISTER", "订阅取消: ID=" + id + ", 设备=" + sub.getDeviceId());
            response.put("status", "success");
            response.put("message", "订阅已取消");
        } else {
            response.put("status", "error");
            response.put("message", "订阅不存在: " + id);
        }
        return response;
    }

    /**
     * 查询所有订阅
     */
    @GetMapping("/list")
    public Map<String, Object> listSubscriptions() {
        log.info("查询订阅列表");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", new ArrayList<>(subscriptions.values()));
        response.put("count", subscriptions.size());
        return response;
    }

    /**
     * 模拟触发推送
     */
    @PostMapping("/trigger/{id}")
    public Map<String, Object> triggerPush(@PathVariable("id") Long id) {
        log.info("触发推送: subscriptionId={}", id);

        Map<String, Object> response = new HashMap<>();
        Subscription sub = subscriptions.get(id);
        if (sub == null) {
            response.put("status", "error");
            response.put("message", "订阅不存在: " + id);
            return response;
        }

        // 模拟推送
        sub.setPushCount(sub.getPushCount() + 1);
        sub.setLastPushAt(Instant.now().toString());

        logEvent("PUSH", "数据推送成功: 订阅ID=" + id + ", 设备=" + sub.getDeviceId() 
                + ", 累计推送=" + sub.getPushCount());

        response.put("status", "success");
        response.put("message", "推送触发成功");
        response.put("pushCount", sub.getPushCount());
        response.put("lastPushAt", sub.getLastPushAt());
        return response;
    }

    /**
     * 查询分发事件日志
     */
    @GetMapping("/events")
    public Map<String, Object> getEvents(
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        log.info("查询分发事件日志");

        Map<String, Object> response = new HashMap<>();
        int size = eventLog.size();
        int from = Math.max(0, size - limit);
        List<Map<String, Object>> recentEvents = new ArrayList<>(eventLog.subList(from, size));
        Collections.reverse(recentEvents);

        response.put("status", "success");
        response.put("data", recentEvents);
        response.put("total", size);
        return response;
    }

    private void logEvent(String type, String message) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", type);
        event.put("message", message);
        event.put("timestamp", Instant.now().toString());
        eventLog.add(event);
        if (eventLog.size() > 500) {
            eventLog.subList(0, eventLog.size() - 500).clear();
        }
    }

    @Data
    public static class SubscriptionRequest {
        private String deviceId;
        private String sensorType;
        private String callbackUrl;
        private String strategy; // on_change, periodic, threshold
    }

    @Data
    public static class Subscription {
        private long id;
        private String deviceId;
        private String sensorType;
        private String callbackUrl;
        private String strategy;
        private String status;
        private String createdAt;
        private int pushCount;
        private String lastPushAt;
    }
}
