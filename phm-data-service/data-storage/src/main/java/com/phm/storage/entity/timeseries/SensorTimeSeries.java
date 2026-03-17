package com.phm.storage.entity.timeseries;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * 传感器时序数据实体
 * 
 * P0: 列分解存储 - TimescaleDB Hypertable自动分区，自研引擎接口预留
 * P0: 聚簇存储 - 数据库原生分区策略，自定义策略接口预留
 * 
 * TODO: 待TimescaleDB Hypertable优化
 * - 建议将本表转换为TimescaleDB超表：SELECT create_hypertable('sensor_timeseries', 'timestamp');
 * - 考虑数据保留策略：使用TimescaleDB的drop_chunks自动清理过期数据
 */
@Data
@Entity
@Table(name = "sensor_timeseries")
public class SensorTimeSeries {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 设备ID
     * TODO: 考虑使用分区键以支持大规模设备数据存储
     */
    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;
    
    /**
     * 传感器类型
     */
    @Column(name = "sensor_type", nullable = false, length = 32)
    private String sensorType;
    
    /**
     * 时间戳（UTC）
     * 注意：此字段为时序数据核心字段，将来要做分区键
     */
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;
    
    /**
     * 传感器数值
     */
    @Column(name = "sensor_value", nullable = false)
    private Double value;
    
    /**
     * 元数据（JSON格式存储）
     * 用于存储额外的传感器属性，如单位、精度、状态等
     */
    @Column(name = "metadata", length = 2048)
    private String metadata;
    
    /**
     * 记录创建时间
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    
    /**
     * 记录更新时间
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
