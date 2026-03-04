package com.phm.storage.repository.timeseries;

import com.phm.storage.entity.timeseries.SensorTimeSeries;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 传感器时序数据 Repository
 * 
 * TODO: 待TimescaleDB Hypertable优化
 * - 考虑使用TimescaleDB的time_bucket函数进行时间窗口聚合查询
 * - 考虑使用continuous aggregates预计算常用统计指标
 * - 考虑添加异步查询方法以支持大数据量查询
 */
@Repository
public interface SensorTimeSeriesRepository extends JpaRepository<SensorTimeSeries, Long> {
    
    /**
     * 按设备ID和时间范围查询传感器数据
     * 
     * TODO: 待TimescaleDB优化 - 使用time_bucket进行降采样查询
     * 
     * @param deviceId 设备ID
     * @param start 开始时间
     * @param end 结束时间
     * @return 传感器数据列表
     */
    List<SensorTimeSeries> findByDeviceIdAndTimestampBetween(
            String deviceId, 
            Instant start, 
            Instant end);
    
    /**
     * 按传感器类型和时间范围查询数据
     * 
     * @param sensorType 传感器类型
     * @param start 开始时间
     * @param end 结束时间
     * @return 传感器数据列表
     */
    List<SensorTimeSeries> findBySensorTypeAndTimestampBetween(
            String sensorType, 
            Instant start, 
            Instant end);
    
    /**
     * 按设备ID和传感器类型查询最新数据
     * 
     * TODO: 待TimescaleDB优化 - 使用last函数直接获取最新值
     * 
     * @param deviceId 设备ID
     * @param sensorType 传感器类型
     * @return 最新的传感器数据
     */
    SensorTimeSeries findTopByDeviceIdAndSensorTypeOrderByTimestampDesc(
            String deviceId, 
            String sensorType);
    
    /**
     * 按设备ID列表和时间范围批量查询（用于多设备分析场景）
     * 
     * TODO: 待TimescaleDB优化 - 考虑使用并行查询提升性能
     * 
     * @param deviceIds 设备ID列表
     * @param start 开始时间
     * @param end 结束时间
     * @return 传感器数据列表
     */
    @Query("SELECT s FROM SensorTimeSeries s WHERE s.deviceId IN :deviceIds AND s.timestamp BETWEEN :start AND :end ORDER BY s.timestamp")
    List<SensorTimeSeries> findByDeviceIdsAndTimestampBetween(
            @Param("deviceIds") List<String> deviceIds,
            @Param("start") Instant start,
            @Param("end") Instant end);
}
