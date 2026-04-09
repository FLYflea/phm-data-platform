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
     * 按设备ID、传感器类型和时间范围查询数据
     * 
     * P0: 聚簇存储优化 - 利用复合索引 idx_device_time 提升查询性能
     * 
     * @param deviceId 设备ID
     * @param sensorType 传感器类型
     * @param start 开始时间
     * @param end 结束时间
     * @return 传感器数据列表
     */
    List<SensorTimeSeries> findByDeviceIdAndSensorTypeAndTimestampBetween(
            String deviceId,
            String sensorType,
            Instant start,
            Instant end);

    /**
     * 按小时聚合统计数据（AVG, MAX, MIN）
     * 
     * P0: 列分解存储 - TimescaleDB DATE_TRUNC函数自动分区聚合
     * TODO: 待TimescaleDB优化 - 使用time_bucket函数替代DATE_TRUNC
     * 
     * @param deviceId 设备ID
     * @param start 开始时间
     * @param end 结束时间
     * @return 每小时聚合统计结果列表
     */
    @Query("SELECT " +
           "  FUNCTION('DATE_TRUNC', 'hour', s.timestamp) as hour, " +
           "  AVG(s.value) as avgValue, " +
           "  MAX(s.value) as maxValue, " +
           "  MIN(s.value) as minValue, " +
           "  COUNT(s) as count " +
           "FROM SensorTimeSeries s " +
           "WHERE s.deviceId = ?1 AND s.timestamp BETWEEN ?2 AND ?3 " +
           "GROUP BY FUNCTION('DATE_TRUNC', 'hour', s.timestamp) " +
           "ORDER BY hour")
    List<java.util.Map<String, Object>> aggregateByHour(String deviceId, Instant start, Instant end);

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
    
    /**
     * 获取所有不重复的设备ID列表
     * 用于前端下拉框动态加载
     */
    @Query("SELECT DISTINCT s.deviceId FROM SensorTimeSeries s ORDER BY s.deviceId")
    List<String> findDistinctDeviceIds();
    
    /**
     * 获取所有不重复的传感器类型列表
     * 用于前端下拉框动态加载
     */
    @Query("SELECT DISTINCT s.sensorType FROM SensorTimeSeries s ORDER BY s.sensorType")
    List<String> findDistinctSensorTypes();
    
    /**
     * 获取指定设备的所有传感器类型
     */
    @Query("SELECT DISTINCT s.sensorType FROM SensorTimeSeries s WHERE s.deviceId = :deviceId ORDER BY s.sensorType")
    List<String> findDistinctSensorTypesByDeviceId(@Param("deviceId") String deviceId);
}
