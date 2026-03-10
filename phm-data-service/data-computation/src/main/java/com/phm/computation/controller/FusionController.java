package com.phm.computation.controller;

import com.phm.computation.entity.FusionResult;
import com.phm.computation.entity.SensorData;
import com.phm.computation.entity.SyncedData;
import com.phm.computation.service.DataFusionService;
import com.phm.computation.service.TimeSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

/**
 * 数据融合与同步控制器
 * 
 * 功能：
 * - 不确定性时间同步
 * - PDA概率数据关联融合
 * - 端到端处理流水线
 * 
 * TODO: 已完成所有核心接口实现 2026-03-09
 * - 不确定性同步算法接口
 * - PDA融合与NN基线对比接口
 * - 端到端流水线接口（同步→融合→存储）
 */
@Slf4j
@RestController
@RequestMapping("/computation")
@RequiredArgsConstructor
public class FusionController {

    private final TimeSyncService timeSyncService;
    private final DataFusionService dataFusionService;
    private final RestTemplate restTemplate;
    
    @Value("${storage.service.url:http://localhost:8103}")
    private String storageServiceUrl;

    /**
     * 不确定性时间同步接口
     * 
     * 算法：基于隶属度的概率同步
     * - 计算初始隶属概率（基于与期望采样率的偏差）
     * - k邻居加权迭代更新
     * - 低概率点插值修复
     * 
     * @param request 包含rawData（原始数据列表）、kNeighbors（邻居数，默认2）、expectedIntervalMs（期望间隔，默认1000ms）
     * @return 同步后数据 + 统计信息
     */
    @PostMapping("/sync/uncertainty")
    public ResponseEntity<Map<String, Object>> uncertaintySync(@RequestBody Map<String, Object> request) {
        long startTime = System.currentTimeMillis();
        log.info("接收到不确定性同步请求");
        
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawDataMaps = (List<Map<String, Object>>) request.get("rawData");
            
            if (rawDataMaps == null || rawDataMaps.isEmpty()) {
                return ResponseEntity.badRequest().body(buildErrorResponse("原始数据不能为空"));
            }
            
            // 解析参数
            int kNeighbors = 2;
            Object kNeighborsObj = request.get("kNeighbors");
            if (kNeighborsObj instanceof Number) {
                kNeighbors = ((Number) kNeighborsObj).intValue();
            }
            
            long expectedIntervalMs = 1000L;
            Object intervalObj = request.get("expectedIntervalMs");
            if (intervalObj instanceof Number) {
                expectedIntervalMs = ((Number) intervalObj).longValue();
            }
            
            // 转换为SensorData
            List<SensorData> rawData = convertToSensorData(rawDataMaps);
            
            // 执行不确定性同步
            List<SyncedData> syncedData = timeSyncService.uncertaintySync(rawData, kNeighbors, expectedIntervalMs);
            
            // 计算统计信息
            double avgConfidence = syncedData.stream()
                    .mapToDouble(SyncedData::getConfidence)
                    .average()
                    .orElse(0.0);
            
            long interpolatedCount = syncedData.stream()
                    .filter(SyncedData::getInterpolated)
                    .count();
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            Map<String, Object> data = new HashMap<>();
            data.put("syncedData", syncedData);
            data.put("inputCount", rawData.size());
            data.put("outputCount", syncedData.size());
            data.put("averageConfidence", avgConfidence);
            data.put("interpolatedCount", interpolatedCount);
            data.put("processingTimeMs", processingTime);
            data.put("algorithm", "uncertainty-sync");
            data.put("parameters", Map.of(
                    "kNeighbors", kNeighbors,
                    "expectedIntervalMs", expectedIntervalMs
            ));
            
            log.info("不确定性同步完成，输入: {}, 输出: {}, 平均置信度: {}, 耗时: {}ms",
                    rawData.size(), syncedData.size(), String.format("%.3f", avgConfidence), processingTime);
            
            return ResponseEntity.ok(buildSuccessResponse(data, "不确定性同步完成"));
            
        } catch (Exception e) {
            log.error("不确定性同步异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(buildErrorResponse("同步失败: " + e.getMessage()));
        }
    }
    
    /**
     * PDA概率数据关联融合接口
     * 
     * 算法：基于高斯分布的概率关联
     * - 时间窗口对齐
     * - 概率计算：P ∝ exp(-error²/2σ²)
     * - 归一化加权融合
     * - 与NN简单平均对比
     * 
     * @param request 包含multiSourceData（多源数据列表的列表）
     * @return PDA融合结果 + NN基线对比
     */
    @PostMapping("/fusion/pda")
    public ResponseEntity<Map<String, Object>> pdaFusion(@RequestBody Map<String, Object> request) {
        long startTime = System.currentTimeMillis();
        log.info("接收到PDA融合请求");
        
        try {
            @SuppressWarnings("unchecked")
            List<List<Map<String, Object>>> multiSourceMaps = 
                    (List<List<Map<String, Object>>>) request.get("multiSourceData");
            
            if (multiSourceMaps == null || multiSourceMaps.isEmpty()) {
                return ResponseEntity.badRequest().body(buildErrorResponse("多源数据不能为空"));
            }
            
            // 转换为多源SensorData
            List<List<SensorData>> multiSourceData = new ArrayList<>();
            for (List<Map<String, Object>> sourceMaps : multiSourceMaps) {
                multiSourceData.add(convertToSensorData(sourceMaps));
            }
            
            // 执行PDA融合
            List<FusionResult> pdaResults = dataFusionService.probabilisticFusion(multiSourceData);
            
            // 执行NN融合（作为基线对比）
            List<FusionResult> nnResults = dataFusionService.nearestNeighborFusion(multiSourceData);
            
            // 计算对比统计
            double pdaAvgVarianceReduction = pdaResults.stream()
                    .mapToDouble(FusionResult::getVarianceReduction)
                    .average()
                    .orElse(0.0);
            
            double nnAvgVarianceReduction = nnResults.stream()
                    .mapToDouble(FusionResult::getVarianceReduction)
                    .average()
                    .orElse(0.0);
            
            double pdaAvgConfidence = pdaResults.stream()
                    .mapToDouble(FusionResult::getConfidence)
                    .average()
                    .orElse(0.0);
            
            double nnAvgConfidence = nnResults.stream()
                    .mapToDouble(FusionResult::getConfidence)
                    .average()
                    .orElse(0.0);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            Map<String, Object> comparison = new HashMap<>();
            comparison.put("pda", Map.of(
                    "results", pdaResults,
                    "avgVarianceReduction", pdaAvgVarianceReduction,
                    "avgConfidence", pdaAvgConfidence,
                    "resultCount", pdaResults.size()
            ));
            comparison.put("nn", Map.of(
                    "results", nnResults,
                    "avgVarianceReduction", nnAvgVarianceReduction,
                    "avgConfidence", nnAvgConfidence,
                    "resultCount", nnResults.size()
            ));
            comparison.put("improvement", Map.of(
                    "varianceReductionGain", pdaAvgVarianceReduction - nnAvgVarianceReduction,
                    "confidenceGain", pdaAvgConfidence - nnAvgConfidence
            ));
            comparison.put("processingTimeMs", processingTime);
            
            log.info("PDA融合完成，PDA方差缩减: {}%, NN方差缩减: {}%, 耗时: {}ms",
                    String.format("%.2f", pdaAvgVarianceReduction * 100),
                    String.format("%.2f", nnAvgVarianceReduction * 100),
                    processingTime);
            
            return ResponseEntity.ok(buildSuccessResponse(comparison, "PDA概率数据关联融合完成"));
            
        } catch (Exception e) {
            log.error("PDA融合异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(buildErrorResponse("融合失败: " + e.getMessage()));
        }
    }
    
    /**
     * 端到端处理流水线接口
     * 
     * 流程：同步 → 融合 → 存储
     * 
     * @param request 包含multiSourceRawData（多源原始数据）、syncParams（同步参数）
     * @return 完整处理链路信息
     */
    @PostMapping("/pipeline")
    public ResponseEntity<Map<String, Object>> pipeline(@RequestBody Map<String, Object> request) {
        long pipelineStartTime = System.currentTimeMillis();
        log.info("接收到端到端流水线请求");
        
        Map<String, Object> pipelineInfo = new HashMap<>();
        pipelineInfo.put("startTime", Instant.now().toString());
        
        try {
            // ========== 步骤1：时间同步 ==========
            long syncStartTime = System.currentTimeMillis();
            
            @SuppressWarnings("unchecked")
            List<List<Map<String, Object>>> multiSourceRawData = 
                    (List<List<Map<String, Object>>>) request.get("multiSourceRawData");
            
            if (multiSourceRawData == null || multiSourceRawData.isEmpty()) {
                return ResponseEntity.badRequest().body(buildErrorResponse("多源原始数据不能为空"));
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> syncParams = request.get("syncParams") instanceof Map ?
                    (Map<String, Object>) request.get("syncParams") : new HashMap<>();
            
            int kNeighbors = syncParams.getOrDefault("kNeighbors", 2) instanceof Integer ?
                    (Integer) syncParams.get("kNeighbors") : 2;
            long expectedIntervalMs = syncParams.getOrDefault("expectedIntervalMs", 1000L) instanceof Number ?
                    ((Number) syncParams.get("expectedIntervalMs")).longValue() : 1000L;
            
            // 对每个数据源进行同步
            List<List<SensorData>> syncedMultiSourceData = new ArrayList<>();
            List<Map<String, Object>> syncDetails = new ArrayList<>();
            
            for (int i = 0; i < multiSourceRawData.size(); i++) {
                List<Map<String, Object>> rawDataMaps = multiSourceRawData.get(i);
                List<SensorData> rawData = convertToSensorData(rawDataMaps);
                
                List<SyncedData> syncedData = timeSyncService.uncertaintySync(rawData, kNeighbors, expectedIntervalMs);
                
                // 转换回SensorData用于融合
                List<SensorData> syncedSensorData = syncedData.stream().map(s -> {
                    SensorData d = new SensorData();
                    d.setDeviceId(s.getDeviceId());
                    d.setSensorType(s.getSensorType());
                    d.setTimestamp(s.getSyncedTimestamp());
                    d.setValue(s.getValue());
                    return d;
                }).collect(java.util.stream.Collectors.toList());
                
                syncedMultiSourceData.add(syncedSensorData);
                
                Map<String, Object> detail = new HashMap<>();
                detail.put("sourceIndex", i);
                detail.put("inputCount", rawData.size());
                detail.put("outputCount", syncedData.size());
                detail.put("avgConfidence", syncedData.stream()
                        .mapToDouble(SyncedData::getConfidence)
                        .average()
                        .orElse(0.0));
                syncDetails.add(detail);
            }
            
            long syncTime = System.currentTimeMillis() - syncStartTime;
            pipelineInfo.put("syncStep", Map.of(
                    "status", "success",
                    "processingTimeMs", syncTime,
                    "details", syncDetails
            ));
            
            // ========== 步骤2：数据融合 ==========
            long fusionStartTime = System.currentTimeMillis();
            
            List<FusionResult> fusionResults = dataFusionService.probabilisticFusion(syncedMultiSourceData);
            
            long fusionTime = System.currentTimeMillis() - fusionStartTime;
            
            double avgFusionConfidence = fusionResults.stream()
                    .mapToDouble(FusionResult::getConfidence)
                    .average()
                    .orElse(0.0);
            
            double avgVarianceReduction = fusionResults.stream()
                    .mapToDouble(FusionResult::getVarianceReduction)
                    .average()
                    .orElse(0.0);
            
            pipelineInfo.put("fusionStep", Map.of(
                    "status", "success",
                    "processingTimeMs", fusionTime,
                    "resultCount", fusionResults.size(),
                    "avgConfidence", avgFusionConfidence,
                    "avgVarianceReduction", avgVarianceReduction
            ));
            
            // ========== 步骤3：存储到storage层 ==========
            long storageStartTime = System.currentTimeMillis();
            int savedCount = 0;
            String storageMessage = "";
            
            try {
                // 转换融合结果为存储格式
                List<Map<String, Object>> storageData = fusionResults.stream().map(result -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("deviceId", "fused-" + result.getFusedSensorType());
                    data.put("sensorType", result.getFusedSensorType());
                    data.put("timestamp", result.getTimestamp().toString());
                    data.put("value", result.getFusedValue());
                    data.put("metadata", Map.of(
                            "fusionMethod", result.getFusionMethod(),
                            "confidence", result.getConfidence(),
                            "sourceCount", result.getSourceCount(),
                            "varianceReduction", result.getVarianceReduction()
                    ));
                    return data;
                }).collect(java.util.stream.Collectors.toList());
                
                // 调用storage服务批量保存
                String storageUrl = storageServiceUrl + "/storage/timeseries/batch";
                ResponseEntity<List> response = restTemplate.postForEntity(storageUrl, storageData, List.class);
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    savedCount = response.getBody() != null ? response.getBody().size() : 0;
                    storageMessage = "融合结果保存成功";
                } else {
                    storageMessage = "融合结果保存失败: " + response.getStatusCode();
                }
                
            } catch (Exception e) {
                storageMessage = "存储服务调用失败: " + e.getMessage();
                log.warn("流水线存储步骤失败: {}", e.getMessage());
            }
            
            long storageTime = System.currentTimeMillis() - storageStartTime;
            pipelineInfo.put("storageStep", Map.of(
                    "status", savedCount > 0 ? "success" : "failed",
                    "processingTimeMs", storageTime,
                    "savedCount", savedCount,
                    "message", storageMessage
            ));
            
            // ========== 汇总 ==========
            long totalTime = System.currentTimeMillis() - pipelineStartTime;
            pipelineInfo.put("endTime", Instant.now().toString());
            pipelineInfo.put("totalProcessingTimeMs", totalTime);
            pipelineInfo.put("fusionResults", fusionResults);
            
            log.info("端到端流水线完成，总耗时: {}ms，融合结果数: {}，保存成功: {}",
                    totalTime, fusionResults.size(), savedCount);
            
            return ResponseEntity.ok(buildSuccessResponse(pipelineInfo, "端到端流水线处理完成"));
            
        } catch (Exception e) {
            log.error("流水线处理异常: {}", e.getMessage(), e);
            pipelineInfo.put("status", "failed");
            pipelineInfo.put("error", e.getMessage());
            return ResponseEntity.status(500).body(buildErrorResponse("流水线处理失败: " + e.getMessage()));
        }
    }
    
    // ==================== 辅助方法 ====================
    
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
                    java.time.Instant instant = java.time.Instant.parse(tsStr);
                    data.setTimestamp(java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault()));
                } catch (Exception e) {
                    data.setTimestamp(java.time.LocalDateTime.parse(tsStr.replace("Z", "")));
                }
            }
            
            result.add(data);
        }
        
        return result;
    }
    
    /**
     * 构建成功响应
     */
    private Map<String, Object> buildSuccessResponse(Object data, String note) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", data);
        response.put("note", note);
        return response;
    }
    
    /**
     * 构建错误响应
     */
    private Map<String, Object> buildErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        response.put("note", "请求处理失败");
        return response;
    }
}
