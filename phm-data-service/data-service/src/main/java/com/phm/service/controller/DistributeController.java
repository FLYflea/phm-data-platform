package com.phm.service.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * P2: 数据分发控制器（接口预留）
 *
 * 职责：Lazy-Automata 分发机制，主动数据推送
 *
 * TODO: P2 待实现
 * - 订阅者注册与管理
 * - 数据变更自动推送
 * - 分发策略配置
 */
@Slf4j
@RestController
@RequestMapping("/service/distribute")
public class DistributeController {

    /**
     * P2: 注册分发订阅（接口预留）
     *
     * @return 预留提示
     */
    @PostMapping("/register")
    public Map<String, Object> register() {
        log.info("P2分发注册接口调用（预留）");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "reserved");
        response.put("note", "Lazy-Automata分发机制待实现");
        response.put("plannedFeatures", Arrays.asList(
            "订阅者注册管理",
            "数据变更监听",
            "自动推送机制",
            "分发策略配置",
            "推送失败重试",
            "批量推送优化"
        ));

        return response;
    }
}
