package com.phm.service.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

/**
 * P0: 数据查询控制器
 *
 * 职责：统一查询入口，根据 dataType 转发到 storage 层对应接口
 * - timeseries → /storage/timeseries/query
 * - graph → /storage/graph/equipment/{id}
 * - document → /storage/document/search
 */
@Slf4j
@RestController
@RequestMapping("/service")
@RequiredArgsConstructor
public class QueryController {

    private final RestTemplate restTemplate;

    @Value("${storage.service.url}")
    private String storageServiceUrl;

    /**
     * P0: 统一查询接口
     *
     * 根据 dataType 转发到不同存储接口：
     * - timeseries: 时序数据查询
     * - graph: 图数据查询
     * - document: 文档搜索
     *
     * @param request 查询请求 { dataType, deviceId, start, end, ... }
     * @return 统一响应格式 { status, data, count }
     */
    @PostMapping("/query")
    public Map<String, Object> query(@RequestBody Map<String, Object> request) {
        String dataType = (String) request.get("dataType");
        log.info("P0统一查询: dataType={}", dataType);

        Map<String, Object> response = new HashMap<>();

        try {
            switch (dataType != null ? dataType.toLowerCase() : "") {
                case "timeseries":
                    response = queryTimeseries(request);
                    break;
                case "graph":
                    response = queryGraph(request);
                    break;
                case "document":
                    response = queryDocument(request);
                    break;
                default:
                    response.put("status", "error");
                    response.put("message", "不支持的dataType: " + dataType);
                    log.warn("不支持的dataType: {}", dataType);
            }
        } catch (Exception e) {
            log.error("查询失败: {}", e.getMessage(), e);
            response.put("status", "error");
            response.put("message", "查询失败: " + e.getMessage());
        }

        return response;
    }

    /**
     * P0: 时序数据查询
     */
    private Map<String, Object> queryTimeseries(Map<String, Object> request) {
        String deviceId = (String) request.get("deviceId");
        String start = (String) request.get("start");
        String end = (String) request.get("end");
        String sensorType = (String) request.get("sensorType");

        String url = UriComponentsBuilder
                .fromHttpUrl(storageServiceUrl + "/storage/timeseries/query")
                .queryParam("deviceId", deviceId)
                .queryParam("start", start)
                .queryParam("end", end)
                .queryParamIfPresent("sensorType", Optional.ofNullable(sensorType))
                .toUriString();

        log.info("P0转发时序查询: {}", url);

        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        Map<String, Object> result = response.getBody();

        // 统一格式
        Map<String, Object> unified = new HashMap<>();
        unified.put("status", "success");
        unified.put("data", result != null ? result.get("data") : Collections.emptyList());
        unified.put("count", result != null ? result.get("count") : 0);
        unified.put("dataType", "timeseries");

        return unified;
    }

    /**
     * P0: 图数据查询
     */
    private Map<String, Object> queryGraph(Map<String, Object> request) {
        String equipmentId = (String) request.get("equipmentId");

        String url = storageServiceUrl + "/storage/graph/equipment/" + equipmentId;
        log.info("P0转发图查询: {}", url);

        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        Map<String, Object> result = response.getBody();

        Map<String, Object> unified = new HashMap<>();
        if (result != null && "success".equals(result.get("status"))) {
            unified.put("status", "success");
            unified.put("data", result.get("equipment"));
            unified.put("components", result.get("components"));
            unified.put("count", result.get("componentCount"));
        } else {
            unified.put("status", "not_found");
            unified.put("data", null);
            unified.put("count", 0);
        }
        unified.put("dataType", "graph");

        return unified;
    }

    /**
     * P0: 文档搜索
     */
    private Map<String, Object> queryDocument(Map<String, Object> request) {
        String keyword = (String) request.get("keyword");

        String url = UriComponentsBuilder
                .fromHttpUrl(storageServiceUrl + "/storage/document/search")
                .queryParam("keyword", keyword != null ? keyword : "")
                .toUriString();

        log.info("P0转发文档查询: {}", url);

        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        Map<String, Object> result = response.getBody();

        Map<String, Object> unified = new HashMap<>();
        unified.put("status", "success");
        unified.put("data", result != null ? result.get("documents") : Collections.emptyList());
        unified.put("count", result != null ? result.get("count") : 0);
        unified.put("dataType", "document");

        return unified;
    }

    /**
     * P2: 复杂查询接口（预留）
     *
     * @return 预留提示
     */
    @PostMapping("/query/complex")
    public Map<String, Object> queryComplex() {
        log.info("P2复杂查询接口调用（预留）");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "reserved");
        response.put("note", "XML查询语言待实现");
        response.put("plannedFeatures", Arrays.asList(
            "XPath查询",
            "JSONPath查询",
            "SQL-like查询",
            "自定义查询DSL"
        ));

        return response;
    }
}
