package com.phm.collection.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 传感器数据实体
 */
@Data
public class SensorData {
    
    /**
     * 设备ID
     */
    private String deviceId;
    
    /**
     * 传感器类型
     */
    private String sensorType;
    
    /**
     * 传感器数值
     */
    private Double value;
    
    /**
     * 时间戳
     */
    private LocalDateTime timestamp;
}
