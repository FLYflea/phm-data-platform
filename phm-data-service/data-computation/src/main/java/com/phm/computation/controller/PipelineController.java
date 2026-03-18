package com.phm.computation.controller;

import com.phm.computation.entity.FusionResult;
import com.phm.computation.entity.SensorData;
import com.phm.computation.entity.SyncedData;
import com.phm.computation.service.DataFusionService;
import com.phm.computation.service.FeatureEngineeringService;
import com.phm.computation.service.TimeSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 数据处理流水线控制器
 * 
 * 职责：提供完整的数据处理链路
 * - 接收原始数据（来自采集层）
 * - 执行时间同步 → 数据融合 → 特征提取
 * - 调用存储层保存结果
 * - 返回完整处理结果
 * 
 * 数据流：采集层 → 计算层(pipeline) → 存储层
 */
@Slf4j
@RestController
@RequestMapping("/computation/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final RestTemplate restTemplate;
    private final TimeSyncService timeSyncService;
    private final DataFusionService dataFusionService;
    private final FeatureEngineeringService featureEngineeringService;

    @Value("${storage.service.url:http://localhost:8103}")
    private String storageServiceUrl;

    /**
     * 完整数据处理流水线
     * 
     * 处理流程：
     * 1. 时间同步（不确定性同步算法）
     * 2. 数据融合（PDA概率数据关联）
     * 3. 特征提取（时域特征）
     * 4. 保存到存储层
     * 
     * @param request 包含 rawData（原始传感器数据列表）
     * @return 完整处理结果
     */
    @PostMapping("/full")
    public ResponseEntity<Map<String, Object>> fullPipeline(@RequestBody Map<String, Object> request) {
        long startTime = System.currentTimeMillis();
        log.info("接收到完整流水线处理请求");

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawDataMaps = (List<Map<String, Object>>) request.get("rawData");
            
            if (rawDataMaps == null || rawDataMaps.isEmpty()) {
                return ResponseEntity.badRequest().body(buildErrorResponse("原始数据不能为空"));
            }

            String deviceId = (String) request.getOrDefault("deviceId", "unknown");
            String sensorType = (String) request.getOrDefault("sensorType", "default");

            // ========== Step 1: 时间同步 ==========
            log.info("Step 1: 执行时间同步，数据条数: {}", rawDataMaps.size());
            List<SensorData> rawData = convertToSensorData(rawDataMaps);
            List<SyncedData> syncedData = timeSyncService.uncertaintySync(rawData, 2, 1000);
            log.info("时间同步完成: {} 条 -> {} 条", rawData.size(), syncedData.size());

            // ========== Step 2: 数据融合 ==========
            log.info("Step 2: 执行数据融合");
            List<List<SensorData>> multiSourceData = new ArrayList<>();
            multiSourceData.add(rawData);
            List<FusionResult> fusionResults = dataFusionService.probabilisticFusion(multiSourceData);
            log.info("数据融合完成: {} 条结果", fusionResults.size());

            // ========== Step 3: 特征提取 ==========
            log.info("Step 3: 执行特征提取");
            List<Double> values = syncedData.stream()
                    .map(SyncedData::getValue)
                    .toList();
            
            Map<String, Double> timeDomainFeatures = featureEngineeringService.extractTimeDomain(values);
            log.info("特征提取完成: {} 个特征", timeDomainFeatures.size());

            // ========== Step 4: 保存到存储层 ==========
            log.info("Step 4: 保存处理结果到存储层");
            List<Map<String, Object>> savedResults = saveToStorage(syncedData, deviceId, sensorType, timeDomainFeatures);
            log.info("存储完成: {} 条数据已保存", savedResults.size());

            // 构建响应
            long processingTime = System.currentTimeMillis() - startTime;
            Map<String, Object> result = new HashMap<>();
            result.put("deviceId", deviceId);
            result.put("sensorType", sensorType);
            result.put("inputCount", rawData.size());
            result.put("syncedCount", syncedData.size());
            result.put("fusionCount", fusionResults.size());
            result.put("savedCount", savedResults.size());
            result.put("features", timeDomainFeatures);
            result.put("processingTimeMs", processingTime);
            result.put("pipeline", Arrays.asList("timeSync", "fusion", "featureExtraction", "storage"));

            log.info("流水线处理完成，总耗时: {}ms", processingTime);
            return ResponseEntity.ok(buildSuccessResponse(result, "数据处理流水线完成"));

        } catch (Exception e) {
            log.error("流水线处理异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(buildErrorResponse("处理失败: " + e.getMessage()));
        }
    }

    /**
     * 简化版流水线 - 仅同步和存储
     */
    @PostMapping("/simple")
    public ResponseEntity<Map<String, Object>> simplePipeline(@RequestBody Map<String, Object> request) {
        long startTime = System.currentTimeMillis();
        log.info("接收到简化流水线处理请求");

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawDataMaps = (List<Map<String, Object>>) request.get("rawData");
            
            if (rawDataMaps == null || rawDataMaps.isEmpty()) {
                return ResponseEntity.badRequest().body(buildErrorResponse("原始数据不能为空"));
            }

            String deviceId = (String) request.getOrDefault("deviceId", "unknown");
            String sensorType = (String) request.getOrDefault("sensorType", "default");

            // 时间同步
            List<SensorData> rawData = convertToSensorData(rawDataMaps);
            List<SensorData> syncedData = timeSyncService.syncByTimestamp(rawData);
            
            // 特征提取（简化流水线也提取基本特征）
            List<Double> values = syncedData.stream()
                    .map(SensorData::getValue)
                    .filter(v -> v != null)
                    .toList();
            log.info("准备提取特征，数据点数: {}, values={}", values.size(), values);
            Map<String, Double> features = featureEngineeringService.extractTimeDomain(values);
            log.info("简化流水线特征提取完成: {} 个特征", features.size());
            
            // 确保 features 不是空 Map
            if (features == null || features.isEmpty()) {
                features = new HashMap<>();
                features.put("mean", values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
                log.info("特征为空，手动计算均值: {}", features.get("mean"));
            }

            // 保存到存储层（包含特征）
            List<Map<String, Object>> savedResults = saveToStorage(syncedData, deviceId, sensorType, features);

            long processingTime = System.currentTimeMillis() - startTime;
            Map<String, Object> result = new HashMap<>();
            result.put("deviceId", deviceId);
            result.put("inputCount", rawData.size());
            result.put("savedCount", savedResults.size());
            result.put("savedResults", savedResults);  // 调试：返回存储层响应
            result.put("features", features);  // 返回提取的特征
            result.put("processingTimeMs", processingTime);

            return ResponseEntity.ok(buildSuccessResponse(result, "简化流水线处理完成"));

        } catch (Exception e) {
            log.error("流水线处理异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(buildErrorResponse("处理失败: " + e.getMessage()));
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 将同步后的数据保存到存储层（包含特征）
     */
    private List<Map<String, Object>> saveToStorage(List<?> syncedData, String deviceId, String sensorType, Map<String, Double> features) {
        List<Map<String, Object>> savedResults = new ArrayList<>();
        String saveUrl = storageServiceUrl + "/storage/timeseries/save";

        for (Object data : syncedData) {
            try {
                Map<String, Object> storageData = new HashMap<>();
                storageData.put("deviceId", deviceId);
                storageData.put("sensorType", sensorType);
                
                // 处理不同类型的数据 - 确保 timestamp 以 String 格式传递，避免 Jackson 序列化为数组
                if (data instanceof SyncedData synced) {
                    Instant ts = synced.getSyncedTimestamp() != null ?
                        synced.getSyncedTimestamp().toInstant(java.time.ZoneOffset.UTC) : Instant.now();
                    storageData.put("timestamp", ts.toString());  // 确保是字符串格式
                    storageData.put("value", synced.getValue());
                    storageData.put("confidence", synced.getConfidence());
                    storageData.put("interpolated", synced.getInterpolated());
                } else if (data instanceof com.phm.computation.entity.SensorData sensor) {
                    Instant ts = sensor.getTimestamp() != null ?
                        sensor.getTimestamp().toInstant(java.time.ZoneOffset.UTC) : Instant.now();
                    storageData.put("timestamp", ts.toString());  // 确保是字符串格式
                    storageData.put("value", sensor.getValue());
                    storageData.put("confidence", 1.0);
                    storageData.put("interpolated", false);
                }

                // 添加特征数据到存储（如果有）
                if (features != null && !features.isEmpty()) {
                    storageData.put("features", features);
                    // 同时展开主要特征作为独立字段，便于查询
                    storageData.put("mean", features.get("mean"));
                    storageData.put("std", features.get("std"));
                    storageData.put("rms", features.get("rms"));
                    storageData.put("peak", features.get("peak"));
                    storageData.put("kurtosis", features.get("kurtosis"));
                }
                
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.postForObject(saveUrl, storageData, Map.class);
                log.info("存储层返回: {}", response);
                
                if (response != null && "success".equals(response.get("status"))) {
                    log.info("数据保存成功，添加到结果");
                    savedResults.add(response);
                } else {
                    log.warn("数据保存失败或响应异常: {}", response);
                }
            } catch (Exception e) {
                log.warn("保存单条数据失败: {}", e.getMessage());
            }
        }

        return savedResults;
    }

    /**
     * 将Map列表转换为SensorData列表
     */
    private List<SensorData> convertToSensorData(List<Map<String, Object>> dataMaps) {
        List<SensorData> result = new ArrayList<>();
        
        for (Map<String, Object> map : dataMaps) {
            SensorData data = new SensorData();
            data.setDeviceId((String) map.get("deviceId"));
            data.setSensorType((String) map.get("sensorType"));
            
            Object value = map.get("value");
            if (value instanceof Number) {
                data.setValue(((Number) value).doubleValue());
            }
            
            Object timestamp = map.get("timestamp");
            if (timestamp instanceof String) {
                String tsStr = (String) timestamp;
                try {
                    Instant instant = Instant.parse(tsStr);
                    data.setTimestamp(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
                } catch (Exception e) {
                    data.setTimestamp(LocalDateTime.parse(tsStr.replace("Z", "")));
                }
            }
            
            result.add(data);
        }
        
        return result;
    }

    private Map<String, Object> buildSuccessResponse(Object data, String note) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", data);
        response.put("note", note);
        return response;
    }

    private Map<String, Object> buildErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        return response;
    }
}
