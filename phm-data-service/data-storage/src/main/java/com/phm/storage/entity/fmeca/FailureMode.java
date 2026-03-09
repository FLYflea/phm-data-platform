package com.phm.storage.entity.fmeca;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * FMECA: 故障模式实体
 * 
 * 功能：存储设备故障模式分析数据
 */
@Data
public class FailureMode {
    
    /**
     * 故障模式ID
     */
    private String failureModeId;
    
    /**
     * 关联设备ID
     */
    private String equipmentId;
    
    /**
     * 关联组件ID（可选）
     */
    private String componentId;
    
    /**
     * 故障模式名称
     */
    private String name;
    
    /**
     * 故障模式描述
     */
    private String description;
    
    /**
     * 故障原因列表
     */
    private List<String> causes;
    
    /**
     * 故障影响（局部影响）
     */
    private String localEffect;
    
    /**
     * 故障影响（最终影响）
     */
    private String finalEffect;
    
    /**
     * 严酷度类别（1-4，4最严重）
     */
    private Integer severityClass;
    
    /**
     * 发生概率等级（A-E，A最高）
     */
    private String occurrenceLevel;
    
    /**
     * 检测难度等级（1-5，5最难）
     */
    private Integer detectionLevel;
    
    /**
     * 风险优先级数 RPN = 严酷度 × 发生度 × 检测度
     */
    private Integer rpn;
    
    /**
     * 改进措施
     */
    private List<String> improvementMeasures;
    
    /**
     * 数据来源（文档ID）
     */
    private String sourceDocId;
    
    /**
     * 创建时间
     */
    private Instant createdAt;
    
    /**
     * 更新时间
     */
    private Instant updatedAt;
}
