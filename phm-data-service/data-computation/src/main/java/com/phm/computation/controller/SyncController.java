package com.phm.computation.controller;

import com.phm.computation.entity.SensorData;
import com.phm.computation.entity.SyncedData;
import com.phm.computation.service.TimeSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 时间同步控制器
 * 
 * 功能：
 * - 基础时间戳排序同步
 * - 不确定性同步算法
 * - 采样率对齐（聚合/插值）
 * 
 * TODO: 已完成所有同步接口实现 2026-03-09
 * - 基础排序同步接口
 * - 不确定性同步接口（带置信度）
 * - 采样率对齐接口（支持aggregation和interpolation）
 */
@Slf4j
@RestController
@RequestMapping("/sync")
@RequiredArgsConstructor
public class SyncController {

    private final TimeSyncService timeSyncService;

    /**
     * 基础时间同步：按时间戳排序
     * 
     * @param dataList 传感器数据列表
     * @return 按时间戳排序后的数据列表
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> timeSync(@RequestBody List<Map<String, Object>> dataList) {
        long startTime = System.currentTimeMillis();
        log.info("接收到基础时间同步请求，数据条数: {}", dataList.size());
        
        try {
            List<SensorData> sensorDataList = convertToSensorData(dataList);
            List<SensorData> sortedList = timeSyncService.syncByTimestamp(sensorDataList);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            Map<String, Object> data = new HashMap<>();
            data.put("sortedData", sortedList);
            data.put("inputCount", sensorDataList.size());
            data.put("outputCount", sortedList.size());
            data.put("processingTimeMs", processingTime);
            data.put("algorithm", "timestamp-sort");
            
            log.info("基础时间同步完成，输入: {}, 输出: {}, 耗时: {}ms",
                    sensorDataList.size(), sortedList.size(), processingTime);
            
            return ResponseEntity.ok(buildSuccessResponse(data, "时间同步完成"));
            
        } catch (Exception e) {
            log.error("时间同步异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(buildErrorResponse("同步失败: " + e.getMessage()));
        }
    }
    
    /**
     * 不确定性时间同步接口（带置信度）
     * 
     * 算法：基于隶属度的概率同步
     * - 隶属度 = 1 / (1 + |实际间隔-期望间隔|/期望间隔)
     * - k邻居加权迭代更新
     * - 低概率点插值修复
     * 
     * @param request 包含rawData、kNeighbors（默认2）、expectedIntervalMs（默认1000）
     * @return 同步后数据（带置信度）+ 统计信息
     */
    @PostMapping("/uncertainty")
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
            if (request.get("kNeighbors") instanceof Number) {
                kNeighbors = ((Number) request.get("kNeighbors")).intValue();
            }
            
            long expectedIntervalMs = 1000;
            if (request.get("expectedIntervalMs") instanceof Number) {
                expectedIntervalMs = ((Number) request.get("expectedIntervalMs")).longValue();
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
            
            log.info("不确定性同步完成，输入: {}, 输出: {}, 平均置信度: {}, 插值修复: {}, 耗时: {}ms",
                    rawData.size(), syncedData.size(), 
                    String.format("%.3f", avgConfidence),
                    interpolatedCount,
                    processingTime);
            
            return ResponseEntity.ok(buildSuccessResponse(data, "不确定性同步完成"));
            
        } catch (Exception e) {
            log.error("不确定性同步异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(buildErrorResponse("同步失败: " + e.getMessage()));
        }
    }
    
    /**
     * 采样率对齐接口
     * 
     * @param request 包含data（数据列表）、targetRateHz（目标采样率Hz）、method（方法：aggregation/interpolation）
     * @return 对齐后的数据列表
     */
    @PostMapping("/align")
    public ResponseEntity<Map<String, Object>> alignSamplingRate(@RequestBody Map<String, Object> request) {
        long startTime = System.currentTimeMillis();
        log.info("接收到采样率对齐请求");
        
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dataMaps = (List<Map<String, Object>>) request.get("data");
            
            if (dataMaps == null || dataMaps.isEmpty()) {
                return ResponseEntity.badRequest().body(buildErrorResponse("数据不能为空"));
            }
            
            // 解析参数
            int targetRateHz = 1;
            if (request.get("targetRateHz") instanceof Number) {
                targetRateHz = ((Number) request.get("targetRateHz")).intValue();
            }
            
            String method = "aggregation";
            if (request.get("method") instanceof String) {
                method = (String) request.get("method");
            }
            
            // 转换为SensorData
            List<SensorData> data = convertToSensorData(dataMaps);
            
            // 执行采样率对齐
            List<SyncedData> alignedData = timeSyncService.alignSamplingRate(data, targetRateHz, method);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            // 计算统计
            double avgConfidence = alignedData.stream()
                    .mapToDouble(SyncedData::getConfidence)
                    .average()
                    .orElse(0.0);
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("originalCount", data.size());
            stats.put("alignedCount", alignedData.size());
            stats.put("targetRateHz", targetRateHz);
            stats.put("method", method);
            stats.put("averageConfidence", avgConfidence);
            stats.put("processingTimeMs", processingTime);
            
            Map<String, Object> result = new HashMap<>();
            result.put("alignedData", alignedData);
            result.put("statistics", stats);
            
            log.info("采样率对齐完成，原始: {}条, 对齐后: {}条, 目标采样率: {}Hz, 方法: {}, 耗时: {}ms",
                    data.size(), alignedData.size(), targetRateHz, method, processingTime);
            
            return ResponseEntity.ok(buildSuccessResponse(result, "采样率对齐完成"));
            
        } catch (Exception e) {
            log.error("采样率对齐异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(buildErrorResponse("对齐失败: " + e.getMessage()));
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
                // 解析 ISO 格式时间字符串 (2026-03-05T10:00:00Z)
                String tsStr = (String) timestamp;
                try {
                    Instant instant = Instant.parse(tsStr);
                    data.setTimestamp(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
                } catch (Exception e) {
                    // 尝试直接解析 LocalDateTime
                    data.setTimestamp(LocalDateTime.parse(tsStr.replace("Z", "")));
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
