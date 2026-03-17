package com.phm.storage.config;

import com.phm.storage.entity.timeseries.SensorTimeSeries;
import com.phm.storage.repository.timeseries.SensorTimeSeriesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 测试数据初始化器
 * 应用启动时自动生成测试时序数据，方便前后端联调
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestDataInitializer {

    private final SensorTimeSeriesRepository timeSeriesRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @EventListener(ApplicationReadyEvent.class)
    public void initData() {
        try {
            // 检查是否已有数据
            if (timeSeriesRepository.count() > 0) {
                log.info("数据库已有 {} 条数据，跳过初始化", timeSeriesRepository.count());
                return;
            }

            log.info("开始初始化测试数据...");
            List<SensorTimeSeries> testData = new ArrayList<>();
            Random random = new Random(42); // 固定种子，保证数据一致性

            String[] deviceIds = {"EQ-001", "EQ-002", "EQ-003"};
            String[] sensorTypes = {"temperature", "vibration", "pressure", "current"};
            
            Instant baseTime = Instant.now().minus(6, ChronoUnit.DAYS);

            for (String deviceId : deviceIds) {
                for (String sensorType : sensorTypes) {
                    // 为每个设备-传感器组合生成100条数据
                    for (int i = 0; i < 100; i++) {
                        SensorTimeSeries data = new SensorTimeSeries();
                        data.setDeviceId(deviceId);
                        data.setSensorType(sensorType);
                        data.setTimestamp(baseTime.plus(i * 60, ChronoUnit.MINUTES)); // 每分钟一条
                        
                        // 生成合理的随机值
                        double baseValue = getBaseValue(sensorType);
                        double noise = (random.nextDouble() - 0.5) * getRange(sensorType);
                        data.setValue(Math.round((baseValue + noise) * 100.0) / 100.0);
                        
                        // 添加特征数据
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("mean", Math.round((baseValue + noise * 0.1) * 100.0) / 100.0);
                        metadata.put("std", Math.round(random.nextDouble() * 5 * 100.0) / 100.0);
                        metadata.put("rms", Math.round((baseValue + noise * 0.2) * 100.0) / 100.0);
                        metadata.put("peak", Math.round((baseValue + Math.abs(noise) * 2) * 100.0) / 100.0);
                        try {
                            data.setMetadata(objectMapper.writeValueAsString(metadata));
                        } catch (JsonProcessingException e) {
                            log.warn("序列化metadata失败: {}", e.getMessage());
                        }
                        
                        testData.add(data);
                    }
                }
            }

            // 批量保存
            timeSeriesRepository.saveAll(testData);
            log.info("测试数据初始化完成！共生成 {} 条数据", testData.size());
        } catch (Exception e) {
            log.error("测试数据初始化失败: {}", e.getMessage());
        }
    }

    /**
     * 获取传感器类型的基础值
     */
    private double getBaseValue(String sensorType) {
        return switch (sensorType) {
            case "temperature" -> 45.0;
            case "vibration" -> 12.5;
            case "pressure" -> 101.3;
            case "current" -> 5.2;
            default -> 10.0;
        };
    }

    /**
     * 获取传感器类型的波动范围
     */
    private double getRange(String sensorType) {
        return switch (sensorType) {
            case "temperature" -> 20.0;
            case "vibration" -> 10.0;
            case "pressure" -> 5.0;
            case "current" -> 2.0;
            default -> 5.0;
        };
    }
}
