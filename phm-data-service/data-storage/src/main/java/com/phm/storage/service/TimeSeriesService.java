package com.phm.storage.service;

import com.phm.storage.entity.timeseries.SensorTimeSeries;
import com.phm.storage.repository.timeseries.SensorTimeSeriesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 时序数据服务
 *
 * P0: 列分解存储 - TimescaleDB Hypertable自动分区，自研引擎接口预留
 * P0: 聚簇存储 - 数据库原生分区策略，自定义策略接口预留
 *
 * 功能：
 * - 单条/批量时序数据保存
 * - 时间范围查询
 * - 小时级聚合统计
 *
 * TODO: 待TimescaleDB优化
 * - 使用COPY命令批量导入替代INSERT
 * - 使用continuous aggregates预计算聚合指标
 * - 添加异步保存方法支持高并发写入
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimeSeriesService {

    private final SensorTimeSeriesRepository sensorTimeSeriesRepository;

    /**
     * 单条保存传感器时序数据
     *
     * P0: 列分解存储 - 单条INSERT，批量场景建议使用saveBatch
     *
     * @param data 传感器时序数据
     * @return 保存后的数据（包含生成的ID）
     */
    @Transactional("transactionManager")
    public SensorTimeSeries save(SensorTimeSeries data) {
        if (data == null) {
            log.warn("保存失败：数据为空");
            return null;
        }

        log.debug("保存单条时序数据: deviceId={}, sensorType={}, timestamp={}",
                data.getDeviceId(), data.getSensorType(), data.getTimestamp());

        SensorTimeSeries saved = sensorTimeSeriesRepository.save(data);
        log.info("单条数据保存成功: id={}, deviceId={}", saved.getId(), saved.getDeviceId());
        return saved;
    }

    /**
     * 批量保存传感器时序数据
     *
     * P0: 列分解存储 - 批量INSERT优化
     * TODO: 待TimescaleDB优化 - 使用COPY命令替代批量INSERT提升性能
     *
     * @param dataList 传感器时序数据列表
     * @return 保存后的数据列表
     */
    @Transactional("transactionManager")
    public List<SensorTimeSeries> saveBatch(List<SensorTimeSeries> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            log.warn("批量保存失败：数据列表为空");
            return List.of();
        }

        log.info("批量保存时序数据，条数: {}", dataList.size());

        // TODO: 待优化 - 大数据量时考虑分批处理（每批10000条）
        if (dataList.size() > 10000) {
            log.warn("批量数据过大: {}，建议分批处理", dataList.size());
        }

        List<SensorTimeSeries> savedList = sensorTimeSeriesRepository.saveAll(dataList);
        log.info("批量数据保存成功，条数: {}", savedList.size());
        return savedList;
    }

    /**
     * 按时间范围查询传感器数据
     *
     * P0: 聚簇存储 - 利用idx_device_time索引加速范围查询
     *
     * @param deviceId 设备ID
     * @param start 开始时间
     * @param end 结束时间
     * @return 传感器数据列表
     */
    public List<SensorTimeSeries> queryByTimeRange(String deviceId, Instant start, Instant end) {
        if (deviceId == null || deviceId.isEmpty()) {
            log.warn("查询失败：设备ID为空");
            return List.of();
        }

        if (start == null || end == null) {
            log.warn("查询失败：时间范围为空");
            return List.of();
        }

        if (start.isAfter(end)) {
            log.warn("查询失败：开始时间大于结束时间");
            return List.of();
        }

        log.info("查询时序数据: deviceId={}, start={}, end={}", deviceId, start, end);

        List<SensorTimeSeries> result = sensorTimeSeriesRepository
                .findByDeviceIdAndTimestampBetween(deviceId, start, end);

        log.info("查询完成，返回 {} 条数据", result.size());
        return result;
    }

    /**
     * 按设备ID、传感器类型和时间范围查询
     *
     * P0: 聚簇存储 - 复合条件查询，利用索引优化
     *
     * @param deviceId 设备ID
     * @param sensorType 传感器类型
     * @param start 开始时间
     * @param end 结束时间
     * @return 传感器数据列表
     */
    public List<SensorTimeSeries> queryByDeviceAndSensorType(String deviceId, String sensorType,
                                                              Instant start, Instant end) {
        if (deviceId == null || deviceId.isEmpty() || sensorType == null || sensorType.isEmpty()) {
            log.warn("查询失败：设备ID或传感器类型为空");
            return List.of();
        }

        log.info("查询时序数据: deviceId={}, sensorType={}, start={}, end={}",
                deviceId, sensorType, start, end);

        List<SensorTimeSeries> result = sensorTimeSeriesRepository
                .findByDeviceIdAndSensorTypeAndTimestampBetween(deviceId, sensorType, start, end);

        log.info("查询完成，返回 {} 条数据", result.size());
        return result;
    }

    /**
     * 按小时聚合统计
     *
     * P0: 列分解存储 - TimescaleDB DATE_TRUNC函数自动分区聚合
     * 返回每小时的数据统计：平均值、最大值、最小值、数据点数
     *
     * @param deviceId 设备ID
     * @param start 开始时间
     * @param end 结束时间
     * @return 每小时聚合统计结果列表
     */
    public List<Map<String, Object>> aggregateByHour(String deviceId, Instant start, Instant end) {
        if (deviceId == null || deviceId.isEmpty()) {
            log.warn("聚合查询失败：设备ID为空");
            return List.of();
        }

        if (start == null || end == null || start.isAfter(end)) {
            log.warn("聚合查询失败：时间范围无效");
            return List.of();
        }

        log.info("按小时聚合统计: deviceId={}, start={}, end={}", deviceId, start, end);

        List<Map<String, Object>> result = sensorTimeSeriesRepository.aggregateByHour(deviceId, start, end);

        log.info("聚合统计完成，返回 {} 小时的数据", result.size());
        return result;
    }

    /**
     * 获取最新数据
     *
     * @param deviceId 设备ID
     * @param sensorType 传感器类型
     * @return 最新的传感器数据
     */
    public SensorTimeSeries getLatestData(String deviceId, String sensorType) {
        if (deviceId == null || deviceId.isEmpty() || sensorType == null || sensorType.isEmpty()) {
            log.warn("查询失败：设备ID或传感器类型为空");
            return null;
        }

        log.debug("查询最新数据: deviceId={}, sensorType={}", deviceId, sensorType);

        return sensorTimeSeriesRepository
                .findTopByDeviceIdAndSensorTypeOrderByTimestampDesc(deviceId, sensorType);
    }

    /**
     * 统计指定时间范围内的数据条数
     *
     * @param deviceId 设备ID
     * @param start 开始时间
     * @param end 结束时间
     * @return 数据条数
     */
    public long countByTimeRange(String deviceId, Instant start, Instant end) {
        return queryByTimeRange(deviceId, start, end).size();
    }
}
