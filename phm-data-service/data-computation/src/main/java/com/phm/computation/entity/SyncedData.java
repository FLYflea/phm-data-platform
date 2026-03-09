package com.phm.computation.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步后的传感器数据实体
 * 
 * 功能：
 * - 存储时间同步后的数据
 * - 包含同步置信度和同步方法信息
 * 
 * TODO: 已完成不确定性同步算法实现 2026-03-09
 * - 实现了基于隶属度的概率同步算法
 * - 支持邻居加权迭代更新
 * - 低概率点插值修复
 */
@Data
public class SyncedData {
    
    /**
     * 设备ID
     */
    private String deviceId;
    
    /**
     * 传感器类型
     */
    private String sensorType;
    
    /**
     * 同步后的时间戳
     */
    private LocalDateTime syncedTimestamp;
    
    /**
     * 传感器数值
     */
    private Double value;
    
    /**
     * 同步置信度（0-1之间，基于隶属度计算）
     */
    private Double confidence;
    
    /**
     * 同步方法标识
     * - uncertainty：不确定性同步
     * - aggregation：聚合下采样
     * - interpolation：插值上采样
     */
    private String syncMethod;
    
    /**
     * 原始时间戳（用于对比）
     */
    private LocalDateTime originalTimestamp;
    
    /**
     * 是否经过插值修复
     */
    private Boolean interpolated;
}
