package com.phm.storage.controller;

import com.phm.storage.entity.timeseries.SensorTimeSeries;
import com.phm.storage.repository.timeseries.SensorTimeSeriesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * 数据存储控制器
 * 
 * TODO: 待生产优化
 * - 考虑添加数据校验和清洗逻辑
 * - 考虑添加异步批量写入以提升吞吐量
 * - 考虑添加数据压缩和归档策略
 * - 考虑添加写入限流和背压机制
 */
@Slf4j
@RestController
@RequestMapping("/storage/timeseries")
@RequiredArgsConstructor
public class StorageController {
    
    private final SensorTimeSeriesRepository sensorTimeSeriesRepository;
    
    /**
     * 单条保存传感器时序数据
     * 
     * TODO: 待优化 - 考虑使用消息队列削峰填谷
     * 
     * @param data 传感器数据
     * @return 保存后的数据（包含生成的ID）
     */
    @PostMapping("/save")
    public SensorTimeSeries saveSingle(@RequestBody SensorTimeSeries data) {
        log.info("保存单条传感器数据: deviceId={}, sensorType={}, timestamp={}", 
                data.getDeviceId(), data.getSensorType(), data.getTimestamp());
        
        SensorTimeSeries saved = sensorTimeSeriesRepository.save(data);
        log.info("单条数据保存成功: id={}", saved.getId());
        return saved;
    }
    
    /**
     * 批量保存传感器时序数据
     * 
     * TODO: 待TimescaleDB优化 - 使用COPY命令替代INSERT批量导入
     * 
     * @param dataList 传感器数据列表
     * @return 保存后的数据列表
     */
    @PostMapping("/batch")
    public List<SensorTimeSeries> saveBatch(@RequestBody List<SensorTimeSeries> dataList) {
        log.info("批量保存传感器数据，条数: {}", dataList.size());
        
        // TODO: 考虑添加批量大小限制和分批处理
        if (dataList.size() > 10000) {
            log.warn("批量数据过大: {}，建议分批处理", dataList.size());
        }
        
        List<SensorTimeSeries> savedList = sensorTimeSeriesRepository.saveAll(dataList);
        log.info("批量数据保存成功，条数: {}", savedList.size());
        return savedList;
    }
    
    /**
     * 按设备ID和时间范围查询传感器数据
     * 
     * TODO: 待TimescaleDB优化 - 使用time_bucket进行降采样查询以减少数据量
     * 
     * @param deviceId 设备ID
     * @param start 开始时间（ISO-8601格式）
     * @param end 结束时间（ISO-8601格式）
     * @return 传感器数据列表
     */
    @GetMapping("/query")
    public List<SensorTimeSeries> queryByDeviceAndTimeRange(
            @RequestParam String deviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        
        log.info("查询传感器数据: deviceId={}, start={}, end={}", deviceId, start, end);
        
        // TODO: 考虑添加查询结果大小限制和分页支持
        List<SensorTimeSeries> result = sensorTimeSeriesRepository.findByDeviceIdAndTimestampBetween(deviceId, start, end);
        
        log.info("查询完成，返回数据条数: {}", result.size());
        return result;
    }
}
