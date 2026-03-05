package com.phm.collection.controller;

import com.phm.collection.entity.SensorData;
import com.phm.collection.service.ImageParserService;
import com.phm.collection.service.TextParserService;
import com.phm.collection.util.DeltaCompressionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

/**
 * 数据采集控制器
 * 
 * 功能模块：
 * - 传感器数据接收与存储
 * - 批量数据压缩
 * - 图像文档解析
 * - 文本文档解析
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SensorController {

    private final RestTemplate restTemplate;
    private final DeltaCompressionUtil deltaCompressionUtil;
    private final ImageParserService imageParserService;
    private final TextParserService textParserService;

    @Value("${storage.service.url}")
    private String storageServiceUrl;

    // ==================== 传感器数据接口 ====================

    /**
     * 接收单条传感器数据并保存到存储层
     * 
     * @param sensorData 传感器数据
     * @return 统一响应格式
     */
    @PostMapping("/sensor")
    public ResponseEntity<Map<String, Object>> receiveSensorData(@RequestBody SensorData sensorData) {
        log.info("接收到传感器数据: deviceId={}, sensorType={}, value={}, timestamp={}",
                sensorData.getDeviceId(),
                sensorData.getSensorType(),
                sensorData.getValue(),
                sensorData.getTimestamp());

        try {
            // 转换为存储层需要的格式
            SensorTimeSeriesDTO storageData = convertToStorageFormat(sensorData);
            
            // 调用 storage 服务保存数据
            String saveUrl = storageServiceUrl + "/storage/timeseries/save";
            ResponseEntity<SensorTimeSeriesDTO> response = restTemplate.postForEntity(saveUrl, storageData, SensorTimeSeriesDTO.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("数据保存成功: deviceId={}", sensorData.getDeviceId());
                return ResponseEntity.ok(buildSuccessResponse(
                        Map.of("deviceId", sensorData.getDeviceId(), "saved", true),
                        "数据接收并保存成功"
                ));
            } else {
                log.error("数据保存失败: {}", response.getStatusCode());
                return ResponseEntity.status(500).body(buildErrorResponse("数据保存失败: " + response.getStatusCode()));
            }
        } catch (Exception e) {
            log.error("数据保存异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(buildErrorResponse("数据保存异常: " + e.getMessage()));
        }
    }

    /**
     * 批量接收传感器数据，计算 Delta 压缩比
     * 
     * @param sensorDataList 传感器数据列表
     * @return 统一响应格式（包含压缩比信息）
     */
    @PostMapping("/sensor/batch")
    public ResponseEntity<Map<String, Object>> receiveBatchSensorData(@RequestBody List<SensorData> sensorDataList) {
        log.info("接收到批量传感器数据，条数: {}", sensorDataList.size());

        if (sensorDataList.isEmpty()) {
            return ResponseEntity.badRequest().body(buildErrorResponse("数据列表不能为空"));
        }

        try {
            // 提取数值用于压缩计算
            double[] values = sensorDataList.stream()
                    .mapToDouble(SensorData::getValue)
                    .toArray();

            // 计算 Delta 压缩
            byte[] compressed = deltaCompressionUtil.compress(values);
            double compressionRatio = deltaCompressionUtil.calculateCompressionRatio(values, compressed);

            // 转换为存储层需要的格式
            List<SensorTimeSeriesDTO> storageDataList = sensorDataList.stream()
                    .map(this::convertToStorageFormat)
                    .toList();

            // 调用 storage 服务批量保存
            String batchUrl = storageServiceUrl + "/storage/timeseries/batch";
            ResponseEntity<List> response = restTemplate.postForEntity(batchUrl, storageDataList, List.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                int savedCount = response.getBody() != null ? response.getBody().size() : 0;
                log.info("批量数据保存成功，条数: {}, 压缩比: {}%", savedCount, String.format("%.2f", compressionRatio));
                
                Map<String, Object> data = new HashMap<>();
                data.put("savedCount", savedCount);
                data.put("compressionRatio", compressionRatio);
                data.put("originalSize", values.length * 8);
                data.put("compressedSize", compressed.length);
                
                return ResponseEntity.ok(buildSuccessResponse(data, "批量数据接收并保存成功，Delta压缩完成"));
            } else {
                log.error("批量数据保存失败: {}", response.getStatusCode());
                return ResponseEntity.status(500).body(buildErrorResponse("批量数据保存失败: " + response.getStatusCode()));
            }
        } catch (Exception e) {
            log.error("批量数据保存异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(buildErrorResponse("批量数据保存异常: " + e.getMessage()));
        }
    }

    // ==================== 文档解析接口 ====================

    /**
     * 接收图像文件，解析设计图纸
     * 
     * @param file 图像文件
     * @return 解析结果（部件列表）
     */
    @PostMapping("/document/image")
    public ResponseEntity<Map<String, Object>> parseImageDocument(@RequestParam("file") MultipartFile file) {
        log.info("接收到图像文档: {}, 大小: {} 字节", file.getOriginalFilename(), file.getSize());

        try {
            // 验证文件格式
            if (!imageParserService.isSupportedFormat(file.getOriginalFilename())) {
                return ResponseEntity.badRequest().body(buildErrorResponse("不支持的文件格式"));
            }

            // 调用图像解析服务
            List<Map<String, Object>> components = imageParserService.parseDesignImage(file);

            Map<String, Object> data = new HashMap<>();
            data.put("filename", file.getOriginalFilename());
            data.put("componentCount", components.size());
            data.put("components", components);

            return ResponseEntity.ok(buildSuccessResponse(data, "图像解析完成，YOLOv8模型待接入"));
        } catch (Exception e) {
            log.error("图像解析异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(buildErrorResponse("图像解析异常: " + e.getMessage()));
        }
    }

    /**
     * 接收文本内容，解析维修记录或 FMECA 知识
     * 
     * @param request 包含 text 和 type（maintenance/fmeca）的请求
     * @return 解析结果
     */
    @PostMapping("/document/text")
    public ResponseEntity<Map<String, Object>> parseTextDocument(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        String type = request.getOrDefault("type", "maintenance");

        log.info("接收到文本文档，类型: {}, 长度: {} 字符", type, text != null ? text.length() : 0);

        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(buildErrorResponse("文本内容不能为空"));
        }

        try {
            Map<String, Object> parseResult;
            String note;

            if ("fmeca".equalsIgnoreCase(type)) {
                parseResult = textParserService.parseFMECAKnowledge(text);
                note = "FMECA知识解析完成，BERT-BiLSTM-CRF待接入";
            } else {
                parseResult = textParserService.parseMaintenanceText(text);
                note = "维修记录解析完成，BERT-BiLSTM-CRF待接入";
            }

            Map<String, Object> data = new HashMap<>();
            data.put("type", type);
            data.put("parseResult", parseResult);

            return ResponseEntity.ok(buildSuccessResponse(data, note));
        } catch (Exception e) {
            log.error("文本解析异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(buildErrorResponse("文本解析异常: " + e.getMessage()));
        }
    }

    // ==================== 健康检查接口 ====================

    /**
     * 服务健康检查
     * 
     * @return 服务状态和功能列表
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        log.debug("健康检查请求");

        Map<String, Object> data = new HashMap<>();
        data.put("service", "data-collection");
        data.put("port", 8101);
        data.put("status", "UP");
        data.put("timestamp", Instant.now().toString());

        // 功能模块列表
        List<Map<String, Object>> features = new ArrayList<>();
        features.add(buildFeature("sensor", "传感器数据接收", true));
        features.add(buildFeature("compression", "Delta压缩", true));
        features.add(buildFeature("image-parser", "图像解析", true));
        features.add(buildFeature("text-parser", "文本解析", true));
        data.put("features", features);

        return ResponseEntity.ok(buildSuccessResponse(data, "服务运行正常"));
    }

    // ==================== 私有方法 ====================

    /**
     * 将 SensorData 转换为 SensorTimeSeriesDTO 格式
     */
    private SensorTimeSeriesDTO convertToStorageFormat(SensorData sensorData) {
        SensorTimeSeriesDTO dto = new SensorTimeSeriesDTO();
        dto.setDeviceId(sensorData.getDeviceId());
        dto.setSensorType(sensorData.getSensorType());
        dto.setValue(sensorData.getValue());
        
        // 将 LocalDateTime 转换为 Instant (UTC)
        if (sensorData.getTimestamp() != null) {
            dto.setTimestamp(sensorData.getTimestamp().toInstant(ZoneOffset.UTC));
        } else {
            dto.setTimestamp(Instant.now());
        }
        
        return dto;
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

    /**
     * 构建功能模块信息
     */
    private Map<String, Object> buildFeature(String name, String description, boolean available) {
        Map<String, Object> feature = new HashMap<>();
        feature.put("name", name);
        feature.put("description", description);
        feature.put("available", available);
        return feature;
    }

    /**
     * 传感器时序数据传输对象
     * 与 storage 层的 SensorTimeSeries 实体对应
     */
    @lombok.Data
    public static class SensorTimeSeriesDTO {
        private String deviceId;
        private String sensorType;
        private Instant timestamp;
        private Double value;
        private String metadata;
    }
}