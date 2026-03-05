package com.phm.collection.controller;

import com.phm.collection.entity.SensorData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 传感器数据接收控制器
 */
@Slf4j
@RestController
@RequestMapping("/sensor")
@RequiredArgsConstructor
public class SensorController {

    private final RestTemplate restTemplate;

    @Value("${storage.service.url}")
    private String storageServiceUrl;

    /**
     * 接收传感器数据并保存到存储层
     * @param sensorData 传感器数据
     * @return 处理结果
     */
    @PostMapping
    public ResponseEntity<?> receiveSensorData(@RequestBody SensorData sensorData) {
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
                return ResponseEntity.ok("数据接收并保存成功: " + sensorData.getDeviceId());
            } else {
                log.error("数据保存失败: {}", response.getStatusCode());
                return ResponseEntity.status(500).body("数据保存失败");
            }
        } catch (Exception e) {
            log.error("数据保存异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("数据保存异常: " + e.getMessage());
        }
    }

    /**
     * 批量接收传感器数据
     * @param sensorDataList 传感器数据列表
     * @return 处理结果
     */
    @PostMapping("/batch")
    public ResponseEntity<?> receiveBatchSensorData(@RequestBody List<SensorData> sensorDataList) {
        log.info("接收到批量传感器数据，条数: {}", sensorDataList.size());

        if (sensorDataList.isEmpty()) {
            return ResponseEntity.badRequest().body("数据列表不能为空");
        }

        try {
            // 转换为存储层需要的格式
            List<SensorTimeSeriesDTO> storageDataList = sensorDataList.stream()
                    .map(this::convertToStorageFormat)
                    .toList();

            // 调用 storage 服务批量保存
            String batchUrl = storageServiceUrl + "/storage/timeseries/batch";
            ResponseEntity<List> response = restTemplate.postForEntity(batchUrl, storageDataList, List.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                int savedCount = response.getBody() != null ? response.getBody().size() : 0;
                log.info("批量数据保存成功，条数: {}", savedCount);
                return ResponseEntity.ok("批量数据接收并保存成功，条数: " + savedCount);
            } else {
                log.error("批量数据保存失败: {}", response.getStatusCode());
                return ResponseEntity.status(500).body("批量数据保存失败");
            }
        } catch (Exception e) {
            log.error("批量数据保存异常: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("批量数据保存异常: " + e.getMessage());
        }
    }

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