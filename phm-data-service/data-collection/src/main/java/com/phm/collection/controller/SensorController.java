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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

/**
 * 数据采集控制器（已改造）
 * 
 * 职责变更：
 * - 传感器数据接收 → 转发给计算层处理
 * - 图像/文本解析服务 → 供计算层调用
 * 
 * 新数据流：采集层 → 计算层 → 存储层
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SensorController {

    private final RestTemplate restTemplate;
    private final DeltaCompressionUtil deltaCompressionUtil;
    private final ImageParserService imageParserService;
    private final TextParserService textParserService;

    @Value("${computation.service.url:http://localhost:8102}")
    private String computationServiceUrl;

    @Value("${storage.service.url:http://localhost:8103}")
    private String storageServiceUrl;

    // ==================== 传感器数据接口 ====================

    /**
     * 接收单条传感器数据 → 转发给计算层处理
     * 
     * 改造后：采集层只负责接收，计算层负责处理+存储
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
            // 构造流水线请求数据
            List<Map<String, Object>> rawDataList = new ArrayList<>();
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("deviceId", sensorData.getDeviceId());
            dataMap.put("sensorType", sensorData.getSensorType());
            dataMap.put("value", sensorData.getValue());
            // LocalDateTime 转为 UTC Instant 字符串，保留Z标记
            Instant instant = sensorData.getTimestamp() != null ?
                sensorData.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant() :
                Instant.now();
            dataMap.put("timestamp", instant.toString());
            rawDataList.add(dataMap);
            
            // 转发给计算层处理（时间同步→融合→特征→存储）
            String pipelineUrl = computationServiceUrl + "/computation/pipeline/simple";
            Map<String, Object> pipelineRequest = new HashMap<>();
            pipelineRequest.put("rawData", rawDataList);
            pipelineRequest.put("deviceId", sensorData.getDeviceId());
            pipelineRequest.put("sensorType", sensorData.getSensorType());
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(pipelineUrl, pipelineRequest, Map.class);
            
            if (response != null && "success".equals(response.get("status"))) {
                log.info("数据已转发到计算层处理: deviceId={}", sensorData.getDeviceId());
                @SuppressWarnings("unchecked")
                Map<String, Object> resultData = (Map<String, Object>) response.get("data");
                resultData.put("deviceId", sensorData.getDeviceId());
                return ResponseEntity.ok(buildSuccessResponse(resultData, "数据已接收并转发到计算层处理"));
            } else {
                log.error("计算层处理失败: {}", response != null ? response.get("message") : "无响应");
                return ResponseEntity.status(500).body(buildErrorResponse("计算层处理失败"));
            }
        } catch (Exception e) {
            log.error("数据转发异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(buildErrorResponse("数据转发异常: " + e.getMessage()));
        }
    }

    /**
     * 批量接收传感器数据 → 转发给计算层完整流水线处理
     * 
     * 改造后：调用计算层的完整流水线（同步→融合→特征→存储）
     * 
     * @param sensorDataList 传感器数据列表
     * @return 统一响应格式
     */
    @PostMapping("/sensor/batch")
    public ResponseEntity<Map<String, Object>> receiveBatchSensorData(@RequestBody List<SensorData> sensorDataList) {
        log.info("接收到批量传感器数据，条数: {}", sensorDataList.size());

        if (sensorDataList.isEmpty()) {
            return ResponseEntity.badRequest().body(buildErrorResponse("数据列表不能为空"));
        }

        try {
            // 提取数值用于压缩计算（本地计算，不存储）
            double[] values = sensorDataList.stream()
                    .mapToDouble(SensorData::getValue)
                    .toArray();
            byte[] compressed = deltaCompressionUtil.compress(values);
            double compressionRatio = deltaCompressionUtil.calculateCompressionRatio(values, compressed);

            // 转换为原始数据格式
            List<Map<String, Object>> rawDataList = new ArrayList<>();
            String deviceId = sensorDataList.get(0).getDeviceId();
            String sensorType = sensorDataList.get(0).getSensorType();
            
            for (SensorData sensorData : sensorDataList) {
                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("deviceId", sensorData.getDeviceId());
                dataMap.put("sensorType", sensorData.getSensorType());
                dataMap.put("value", sensorData.getValue());
                // LocalDateTime 转为 UTC Instant 字符串，保留Z标记
                Instant instant = sensorData.getTimestamp() != null ?
                    sensorData.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant() :
                    Instant.now();
                dataMap.put("timestamp", instant.toString());
                rawDataList.add(dataMap);
            }

            // 转发给计算层完整流水线处理
            String pipelineUrl = computationServiceUrl + "/computation/pipeline/full";
            Map<String, Object> pipelineRequest = new HashMap<>();
            pipelineRequest.put("rawData", rawDataList);
            pipelineRequest.put("deviceId", deviceId);
            pipelineRequest.put("sensorType", sensorType);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(pipelineUrl, pipelineRequest, Map.class);

            if (response != null && "success".equals(response.get("status"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resultData = (Map<String, Object>) response.get("data");
                resultData.put("compressionRatio", compressionRatio);
                resultData.put("originalSize", values.length * 8);
                resultData.put("compressedSize", compressed.length);
                
                log.info("批量数据已转发到计算层处理，压缩比: {}%", String.format("%.2f", compressionRatio));
                return ResponseEntity.ok(buildSuccessResponse(resultData, 
                    "批量数据已接收并转发到计算层完整流水线处理"));
            } else {
                log.error("计算层处理失败: {}", response != null ? response.get("message") : "无响应");
                return ResponseEntity.status(500).body(buildErrorResponse("计算层处理失败"));
            }
        } catch (Exception e) {
            log.error("批量数据转发异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(buildErrorResponse("批量数据转发异常: " + e.getMessage()));
        }
    }

    /**
     * CSV数据批量导入接口
     * 
     * 支持导入公开数据集（如NASA CMAPSS涡扇发动机数据集）到系统中
     * 
     * @param file CSV文件
     * @param deviceId 设备ID
     * @param sensorColumns 传感器列名，逗号分隔（如 "sensor1,sensor2,sensor3"）
     * @param timestampColumn 时间戳列名（可选，不指定则自动生成）
     * @param delimiter 分隔符（默认逗号）
     * @param skipHeader 是否跳过表头（默认true）
     * @return 导入统计结果
     */
    @PostMapping("/sensor/import-csv")
    public ResponseEntity<Map<String, Object>> importCsvData(
            @RequestParam("file") MultipartFile file,
            @RequestParam("deviceId") String deviceId,
            @RequestParam("sensorColumns") String sensorColumns,
            @RequestParam(value = "timestampColumn", required = false) String timestampColumn,
            @RequestParam(value = "delimiter", defaultValue = ",") String delimiter,
            @RequestParam(value = "skipHeader", defaultValue = "true") boolean skipHeader) {
        
        long startTime = System.currentTimeMillis();
        log.info("CSV数据导入开始: file={}, deviceId={}, sensorColumns={}, timestampColumn={}, delimiter='{}', skipHeader={}",
                file.getOriginalFilename(), deviceId, sensorColumns, timestampColumn, delimiter, skipHeader);

        // 参数校验
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(buildErrorResponse("CSV文件不能为空"));
        }
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(buildErrorResponse("设备ID不能为空"));
        }
        if (sensorColumns == null || sensorColumns.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(buildErrorResponse("传感器列名不能为空"));
        }

        // 解析传感器列名
        String[] sensorColumnArray = sensorColumns.split(",");
        for (int i = 0; i < sensorColumnArray.length; i++) {
            sensorColumnArray[i] = sensorColumnArray[i].trim();
        }

        // 统计变量
        int totalRows = 0;
        int totalDataPoints = 0;
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();

        // 存储解析出的数据
        List<Map<String, Object>> rawDataList = new ArrayList<>();
        Map<String, Integer> columnIndexMap = new HashMap<>();
        Integer timestampColumnIndex = null;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            
            String line;
            int lineNumber = 0;
            Instant baseTimestamp = Instant.now();
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                // 跳过空行
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                String[] columns = line.split(delimiter, -1); // -1保留尾部空字符串
                
                // 处理表头行：建立列名到索引的映射
                if (lineNumber == 1) {
                    for (int i = 0; i < columns.length; i++) {
                        columnIndexMap.put(columns[i].trim(), i);
                    }
                    
                    // 查找时间戳列索引
                    if (timestampColumn != null && !timestampColumn.trim().isEmpty()) {
                        timestampColumnIndex = columnIndexMap.get(timestampColumn.trim());
                        if (timestampColumnIndex == null) {
                            log.warn("未找到时间戳列: {}, 将自动生成时间戳", timestampColumn);
                        }
                    }
                    
                    // 验证传感器列是否存在
                    for (String sensorCol : sensorColumnArray) {
                        if (!columnIndexMap.containsKey(sensorCol)) {
                            // 尝试按索引解析（如果是数字）
                            try {
                                int idx = Integer.parseInt(sensorCol);
                                if (idx >= 0 && idx < columns.length) {
                                    columnIndexMap.put(sensorCol, idx);
                                }
                            } catch (NumberFormatException e) {
                                errors.add("第1行: 未找到传感器列 '" + sensorCol + "'");
                            }
                        }
                    }
                    
                    if (skipHeader) {
                        continue;
                    }
                }
                
                totalRows++;
                
                // 计算当前行的时间戳
                Instant rowTimestamp;
                if (timestampColumnIndex != null && timestampColumnIndex < columns.length) {
                    try {
                        String tsValue = columns[timestampColumnIndex].trim();
                        // 尝试解析时间戳（支持多种格式）
                        rowTimestamp = parseTimestamp(tsValue, baseTimestamp, totalRows);
                    } catch (Exception e) {
                        // 解析失败，使用自动生成
                        rowTimestamp = baseTimestamp.plusSeconds(totalRows - 1);
                    }
                } else {
                    // 无时间戳列，按行号递增自动生成（间馔1秒）
                    rowTimestamp = baseTimestamp.plusSeconds(totalRows - 1);
                }
                
                // 解析每个传感器列的数据
                for (String sensorCol : sensorColumnArray) {
                    Integer colIndex = columnIndexMap.get(sensorCol);
                    if (colIndex == null || colIndex >= columns.length) {
                        failCount++;
                        continue;
                    }
                    
                    String valueStr = columns[colIndex].trim();
                    if (valueStr.isEmpty()) {
                        failCount++;
                        continue;
                    }
                    
                    try {
                        double value = Double.parseDouble(valueStr);
                        
                        Map<String, Object> dataMap = new HashMap<>();
                        dataMap.put("deviceId", deviceId);
                        dataMap.put("sensorType", sensorCol);
                        dataMap.put("value", value);
                        dataMap.put("timestamp", rowTimestamp.toString());
                        rawDataList.add(dataMap);
                        
                        totalDataPoints++;
                        successCount++;
                    } catch (NumberFormatException e) {
                        failCount++;
                        if (errors.size() < 10) {
                            errors.add("第" + lineNumber + "行, 列'" + sensorCol + "': 无法解析数值 '" + valueStr + "'");
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("CSV文件读取异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(buildErrorResponse("CSV文件读取异常: " + e.getMessage()));
        }

        // 批量保存到存储层（CSV批量导入直接存储，不走pipeline计算流程）
        int storedCount = 0;
        if (!rawDataList.isEmpty()) {
            try {
                String saveUrl = storageServiceUrl + "/storage/timeseries/save";
                
                for (Map<String, Object> dataMap : rawDataList) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> response = restTemplate.postForObject(saveUrl, dataMap, Map.class);
                        if (response != null && "success".equals(response.get("status"))) {
                            storedCount++;
                        }
                    } catch (Exception e) {
                        log.warn("单条数据保存失败: {}", e.getMessage());
                    }
                }
                log.info("CSV数据已直接保存到存储层，共{}条", storedCount);
            } catch (Exception e) {
                log.error("数据保存到存储层失败: {}", e.getMessage(), e);
                errors.add("数据保存失败: " + e.getMessage());
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;

        // 构建返回结果
        Map<String, Object> statistics = new LinkedHashMap<>();
        statistics.put("filename", file.getOriginalFilename());
        statistics.put("fileSize", file.getSize());
        statistics.put("deviceId", deviceId);
        statistics.put("sensorColumns", sensorColumnArray);
        statistics.put("totalRows", totalRows);
        statistics.put("totalDataPoints", totalDataPoints);
        statistics.put("successCount", successCount);
        statistics.put("failCount", failCount);
        statistics.put("storedCount", storedCount);
        statistics.put("elapsedMs", elapsed);
        statistics.put("throughput", totalRows > 0 ? String.format("%.2f rows/s", totalRows * 1000.0 / elapsed) : "N/A");
        if (!errors.isEmpty()) {
            statistics.put("errors", errors.size() > 10 ? errors.subList(0, 10) : errors);
            if (errors.size() > 10) {
                statistics.put("errorsTruncated", true);
                statistics.put("totalErrors", errors.size());
            }
        }

        log.info("CSV数据导入完成: 总行数={}, 数据点={}, 成功={}, 失败={}, 存储={}, 耗时={}ms",
                totalRows, totalDataPoints, successCount, failCount, storedCount, elapsed);

        return ResponseEntity.ok(buildSuccessResponse(statistics, 
                "CSV数据导入完成，共解析" + totalRows + "行，生成" + totalDataPoints + "个数据点"));
    }
    
    /**
     * 解析时间戳字符串
     * 支持多种格式：ISO-8601, 纯数字(秒/毫秒), 常见日期格式
     */
    private Instant parseTimestamp(String value, Instant baseTimestamp, int rowNumber) {
        if (value == null || value.trim().isEmpty()) {
            return baseTimestamp.plusSeconds(rowNumber - 1);
        }
        
        value = value.trim();
        
        // 尝试解析为纯数字（秒或毫秒）
        try {
            double numericValue = Double.parseDouble(value);
            // 如果数值大于10^12，认为是毫秒
            if (numericValue > 1e12) {
                return Instant.ofEpochMilli((long) numericValue);
            } else if (numericValue > 1e9) {
                // 认为是秒
                return Instant.ofEpochSecond((long) numericValue);
            } else {
                // 认为是相对时间（秒），从基准时间开始
                return baseTimestamp.plusSeconds((long) numericValue);
            }
        } catch (NumberFormatException e) {
            // 不是纯数字，尝试其他格式
        }
        
        // 尝试ISO-8601格式
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            // 继续尝试其他格式
        }
        
        // 尝试常见日期时间格式: yyyy-MM-dd HH:mm:ss
        try {
            LocalDateTime ldt = LocalDateTime.parse(value.replace(" ", "T"));
            return ldt.atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception e) {
            // 解析失败
        }
        
        // 所有格式都失败，使用自动生成
        return baseTimestamp.plusSeconds(rowNumber - 1);
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
     * [已废弃] 原用于直接转存储层格式
     * 现在数据通过计算层处理后再存储
     */
    @Deprecated
    private SensorTimeSeriesDTO convertToStorageFormat(SensorData sensorData) {
        SensorTimeSeriesDTO dto = new SensorTimeSeriesDTO();
        dto.setDeviceId(sensorData.getDeviceId());
        dto.setSensorType(sensorData.getSensorType());
        dto.setValue(sensorData.getValue());
        
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