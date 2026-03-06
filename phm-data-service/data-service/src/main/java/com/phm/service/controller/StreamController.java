package com.phm.service.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * P2: 数据流控制器（接口预留）
 *
 * 职责：WebSocket 流服务，实时数据推送
 *
 * TODO: P2 待实现
 * - WebSocket 实时订阅
 * - Kafka 集成
 * - 流数据处理
 */
@Slf4j
@RestController
@RequestMapping("/service/stream")
public class StreamController {

    /**
     * P2: 订阅数据流（接口预留）
     *
     * @return 预留提示
     */
    @GetMapping("/subscribe")
    public Map<String, Object> subscribe() {
        log.info("P2数据流订阅接口调用（预留）");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "reserved");
        response.put("note", "WebSocket流服务待实现，后续Kafka集成");
        response.put("plannedFeatures", Arrays.asList(
            "WebSocket实时推送",
            "Kafka消息队列集成",
            "流数据窗口计算",
            "实时告警推送",
            "数据流背压控制"
        ));

        return response;
    }
}
