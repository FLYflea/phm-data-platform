package com.phm.computation.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 数据融合结果实体
 * 
 * 功能：
 * - 存储多源数据融合后的结果
 * - 包含融合权重、置信度和方差缩减信息
 * 
 * TODO: 已完成PDA概率数据关联算法实现 2025-03-05
 * - 实现了基于高斯分布的概率关联
 * - 支持多源加权融合
 * - 计算方差缩减比例评估融合效果
 */
@Data
public class FusionResult {
    
    /**
     * 融合后的传感器类型标识
     */
    private String fusedSensorType;
    
    /**
     * 融合后的数值（加权平均结果）
     */
    private Double fusedValue;
    
    /**
     * 融合时间戳
     */
    private LocalDateTime timestamp;
    
    /**
     * 各数据源权重（Map<deviceId, weight>）
     */
    private Map<String, Double> sourceWeights;
    
    /**
     * 融合置信度（0-1之间）
     */
    private Double confidence;
    
    /**
     * 融合方法标识
     * - PDA：概率数据关联
     * - NN：最近邻关联
     * - JPDA：联合概率数据关联
     */
    private String fusionMethod;
    
    /**
     * 参与融合的数据源个数
     */
    private Integer sourceCount;
    
    /**
     * 融合前方差（多源数据的方差）
     */
    private Double varianceBefore;
    
    /**
     * 融合后方差（融合结果的估计方差）
     */
    private Double varianceAfter;
    
    /**
     * 方差缩减比例（(varianceBefore - varianceAfter) / varianceBefore）
     */
    private Double varianceReduction;
    
    /**
     * 处理耗时（毫秒）
     */
    private Long processingTimeMs;
    
    /**
     * 附加说明信息
     */
    private String note;
}
