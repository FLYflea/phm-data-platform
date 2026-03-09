package com.phm.computation.service;

import com.phm.computation.entity.FusionResult;
import com.phm.computation.entity.SensorData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 多源数据融合服务
 *
 * 功能：
 * - 最近邻关联融合（NN）
 * - 概率数据关联（PDA）- 基于高斯分布假设
 * - 联合概率数据关联（JPDA）- 接口预留
 * 
 * TODO: 已完成PDA概率数据关联算法实现 2026-03-09
 * - 实现了基于高斯分布的概率关联公式：P ∝ exp(-error²/2σ²)
 * - 支持时间窗口对齐的多源数据融合
 * - 计算融合前后方差缩减比例评估融合效果
 * - 返回融合值、各源权重、置信度、处理耗时
 */
@Slf4j
@Service
public class DataFusionService {

    /**
     * 默认时间窗口（毫秒），用于时间对齐
     */
    private static final long DEFAULT_TIME_WINDOW_MS = 500;
    
    /**
     * 默认传感器噪声标准差（用于PDA概率计算）
     */
    private static final double DEFAULT_SENSOR_STD = 0.1;

    /**
     * 概率数据关联融合（PDA核心算法）
     * 
     * 算法步骤：
     * 1. 按时间窗口对齐各源数据
     * 2. 对每个时刻计算各源观测概率（高斯分布：P ∝ exp(-error²/2σ²)）
     * 3. 归一化概率
     * 4. 加权平均：融合值 = Σ(Pi * value_i)
     * 5. 计算融合前后方差缩减比例
     * 
     * @param multiSourceData 多源传感器数据列表的列表（每个子列表代表一个数据源）
     * @return 融合结果列表
     */
    public List<FusionResult> probabilisticFusion(List<List<SensorData>> multiSourceData) {
        long startTime = System.currentTimeMillis();
        
        if (multiSourceData == null || multiSourceData.isEmpty()) {
            log.warn("PDA融合：输入数据为空");
            return Collections.emptyList();
        }
        
        // 过滤空数据源
        multiSourceData = multiSourceData.stream()
                .filter(list -> list != null && !list.isEmpty())
                .collect(Collectors.toList());
        
        if (multiSourceData.isEmpty()) {
            log.warn("PDA融合：所有数据源均为空");
            return Collections.emptyList();
        }
        
        int sourceCount = multiSourceData.size();
        log.info("开始PDA概率数据关联融合，数据源数量: {}", sourceCount);
        
        // 步骤1：按时间窗口对齐各源数据
        List<TimeAlignedGroup> alignedGroups = alignByTimeWindow(multiSourceData, DEFAULT_TIME_WINDOW_MS);
        log.info("时间窗口对齐完成，共 {} 个时间组", alignedGroups.size());
        
        List<FusionResult> results = new ArrayList<>();
        
        // 对每个时间组进行PDA融合
        for (TimeAlignedGroup group : alignedGroups) {
            List<SensorData> groupData = group.getData();
            
            if (groupData.isEmpty()) {
                continue;
            }
            
            // 获取传感器类型（假设同组数据类型相同）
            String sensorType = groupData.get(0).getSensorType();
            LocalDateTime timestamp = group.getRepresentativeTime();
            
            // 步骤2：计算各源观测概率
            // 使用预测值（各源平均值）作为参考
            double predictedValue = groupData.stream()
                    .mapToDouble(SensorData::getValue)
                    .average()
                    .orElse(0.0);
            
            // 计算各源的概率（基于高斯分布）
            // 公式：P ∝ exp(-error²/2σ²)
            Map<String, Double> probabilities = new HashMap<>();
            Map<String, Double> sourceValues = new HashMap<>();
            double totalProb = 0.0;
            
            for (SensorData data : groupData) {
                String deviceId = data.getDeviceId();
                double value = data.getValue();
                double error = Math.abs(value - predictedValue);
                
                // 高斯概率密度（未归一化）
                double probability = Math.exp(-(error * error) / (2 * DEFAULT_SENSOR_STD * DEFAULT_SENSOR_STD));
                
                probabilities.put(deviceId, probability);
                sourceValues.put(deviceId, value);
                totalProb += probability;
            }
            
            // 步骤3：归一化概率
            Map<String, Double> normalizedWeights = new HashMap<>();
            if (totalProb > 0) {
                for (Map.Entry<String, Double> entry : probabilities.entrySet()) {
                    normalizedWeights.put(entry.getKey(), entry.getValue() / totalProb);
                }
            } else {
                // 如果总概率为0，使用等权重
                double equalWeight = 1.0 / groupData.size();
                for (SensorData data : groupData) {
                    normalizedWeights.put(data.getDeviceId(), equalWeight);
                }
            }
            
            // 步骤4：加权平均计算融合值
            double fusedValue = 0.0;
            for (Map.Entry<String, Double> entry : normalizedWeights.entrySet()) {
                String deviceId = entry.getKey();
                double weight = entry.getValue();
                double value = sourceValues.get(deviceId);
                fusedValue += weight * value;
            }
            
            // 步骤5：计算融合前后方差
            double varianceBefore = calculateVariance(new ArrayList<>(sourceValues.values()));
            
            // 融合后方差（基于加权方差公式）
            double varianceAfter = 0.0;
            for (Map.Entry<String, Double> entry : normalizedWeights.entrySet()) {
                String deviceId = entry.getKey();
                double weight = entry.getValue();
                double value = sourceValues.get(deviceId);
                varianceAfter += weight * Math.pow(value - fusedValue, 2);
            }
            
            // 计算方差缩减比例
            double varianceReduction = 0.0;
            if (varianceBefore > 0) {
                varianceReduction = (varianceBefore - varianceAfter) / varianceBefore;
            }
            
            // 融合置信度（基于概率熵）
            double confidence = calculateConfidence(normalizedWeights);
            
            // 构建结果
            FusionResult result = new FusionResult();
            result.setFusedSensorType(sensorType);
            result.setFusedValue(fusedValue);
            result.setTimestamp(timestamp);
            result.setSourceWeights(normalizedWeights);
            result.setConfidence(confidence);
            result.setFusionMethod("PDA");
            result.setSourceCount(groupData.size());
            result.setVarianceBefore(varianceBefore);
            result.setVarianceAfter(varianceAfter);
            result.setVarianceReduction(varianceReduction);
            result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            result.setNote(String.format("PDA融合: %d个数据源, 方差缩减%.2f%%", 
                    groupData.size(), varianceReduction * 100));
            
            results.add(result);
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        log.info("PDA概率数据关联融合完成，输出 {} 条结果，总耗时: {}ms", results.size(), totalTime);
        
        return results;
    }
    
    /**
     * 最近邻关联融合（NN简化实现，作为PDA对比基线）
     * 
     * 实现策略：
     * 1. 按时间窗口对齐数据
     * 2. 对每个窗口内的数据做简单平均
     * 
     * @param multiSourceData 多源传感器数据
     * @return 融合后的结果列表
     */
    public List<FusionResult> nearestNeighborFusion(List<List<SensorData>> multiSourceData) {
        long startTime = System.currentTimeMillis();
        
        if (multiSourceData == null || multiSourceData.isEmpty()) {
            log.warn("NN融合：输入数据为空");
            return Collections.emptyList();
        }
        
        // 过滤空数据源
        multiSourceData = multiSourceData.stream()
                .filter(list -> list != null && !list.isEmpty())
                .collect(Collectors.toList());
        
        if (multiSourceData.isEmpty()) {
            log.warn("NN融合：所有数据源均为空");
            return Collections.emptyList();
        }
        
        log.info("开始最近邻关联融合（NN基线），数据源数量: {}", multiSourceData.size());
        
        // 时间窗口对齐
        List<TimeAlignedGroup> alignedGroups = alignByTimeWindow(multiSourceData, DEFAULT_TIME_WINDOW_MS);
        
        List<FusionResult> results = new ArrayList<>();
        
        for (TimeAlignedGroup group : alignedGroups) {
            List<SensorData> groupData = group.getData();
            
            if (groupData.isEmpty()) {
                continue;
            }
            
            String sensorType = groupData.get(0).getSensorType();
            LocalDateTime timestamp = group.getRepresentativeTime();
            
            // 简单平均（NN基线）
            double fusedValue = groupData.stream()
                    .mapToDouble(SensorData::getValue)
                    .average()
                    .orElse(0.0);
            
            // 等权重
            Map<String, Double> equalWeights = new HashMap<>();
            double equalWeight = 1.0 / groupData.size();
            for (SensorData data : groupData) {
                equalWeights.put(data.getDeviceId(), equalWeight);
            }
            
            // 计算方差
            List<Double> values = groupData.stream()
                    .map(SensorData::getValue)
                    .collect(Collectors.toList());
            double varianceBefore = calculateVariance(values);
            
            FusionResult result = new FusionResult();
            result.setFusedSensorType(sensorType);
            result.setFusedValue(fusedValue);
            result.setTimestamp(timestamp);
            result.setSourceWeights(equalWeights);
            result.setConfidence(0.5); // NN方法置信度固定为0.5
            result.setFusionMethod("NN");
            result.setSourceCount(groupData.size());
            result.setVarianceBefore(varianceBefore);
            result.setVarianceAfter(varianceBefore / groupData.size()); // 理论上减少
            result.setVarianceReduction(1.0 - 1.0 / groupData.size());
            result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            result.setNote(String.format("NN基线: %d个数据源简单平均", groupData.size()));
            
            results.add(result);
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        log.info("NN最近邻融合完成，输出 {} 条结果，总耗时: {}ms", results.size(), totalTime);
        
        return results;
    }
    
    /**
     * 计算融合增益（方差缩减比例）
     * 
     * @param result 融合结果
     * @return 方差缩减比例（0-1之间）
     */
    public double calculateFusionGain(FusionResult result) {
        if (result == null || result.getVarianceBefore() == null || result.getVarianceBefore() == 0) {
            return 0.0;
        }
        return result.getVarianceReduction();
    }
    
    /**
     * 按时间窗口对齐多源数据
     * 
     * @param multiSourceData 多源数据列表
     * @param windowMs 时间窗口大小（毫秒）
     * @return 时间对齐后的数据组列表
     */
    private List<TimeAlignedGroup> alignByTimeWindow(List<List<SensorData>> multiSourceData, long windowMs) {
        // 合并所有数据源的数据
        List<SensorData> allData = new ArrayList<>();
        for (List<SensorData> sourceData : multiSourceData) {
            allData.addAll(sourceData);
        }
        
        // 按时间排序
        allData.sort(Comparator.comparing(SensorData::getTimestamp));
        
        // 按时间窗口分组
        List<TimeAlignedGroup> groups = new ArrayList<>();
        if (allData.isEmpty()) {
            return groups;
        }
        
        List<SensorData> currentGroup = new ArrayList<>();
        currentGroup.add(allData.get(0));
        LocalDateTime currentWindowStart = allData.get(0).getTimestamp();
        
        for (int i = 1; i < allData.size(); i++) {
            SensorData data = allData.get(i);
            long diffMs = ChronoUnit.MILLIS.between(currentWindowStart, data.getTimestamp());
            
            if (diffMs <= windowMs) {
                currentGroup.add(data);
            } else {
                // 保存当前组
                TimeAlignedGroup group = new TimeAlignedGroup();
                group.setData(new ArrayList<>(currentGroup));
                group.setRepresentativeTime(currentGroup.get(0).getTimestamp());
                groups.add(group);
                
                // 开始新组
                currentGroup.clear();
                currentGroup.add(data);
                currentWindowStart = data.getTimestamp();
            }
        }
        
        // 保存最后一组
        if (!currentGroup.isEmpty()) {
            TimeAlignedGroup group = new TimeAlignedGroup();
            group.setData(currentGroup);
            group.setRepresentativeTime(currentGroup.get(0).getTimestamp());
            groups.add(group);
        }
        
        return groups;
    }
    
    /**
     * 计算方差
     */
    private double calculateVariance(List<Double> values) {
        if (values == null || values.size() < 2) {
            return 0.0;
        }
        
        double mean = values.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
        
        return variance;
    }
    
    /**
     * 计算置信度（基于概率分布的熵）
     * 熵越小（权重越集中），置信度越高
     */
    private double calculateConfidence(Map<String, Double> weights) {
        if (weights == null || weights.isEmpty()) {
            return 0.0;
        }
        
        // 计算熵
        double entropy = 0.0;
        for (double weight : weights.values()) {
            if (weight > 0) {
                entropy -= weight * Math.log(weight);
            }
        }
        
        // 归一化熵（最大熵为ln(n)）
        double maxEntropy = Math.log(weights.size());
        if (maxEntropy == 0) {
            return 1.0;
        }
        
        double normalizedEntropy = entropy / maxEntropy;
        
        // 置信度 = 1 - 归一化熵（熵越小置信度越高）
        return Math.max(0.0, Math.min(1.0, 1.0 - normalizedEntropy));
    }
    
    /**
     * 时间对齐数据组（内部类）
     */
    @lombok.Data
    private static class TimeAlignedGroup {
        private List<SensorData> data;
        private LocalDateTime representativeTime;
    }
    
    // ==================== JPDA融合（接口预留） ====================
    
    /**
     * JPDA融合（接口预留）
     * 当前退化为PDA融合
     */
    public List<FusionResult> jpdaFusion(List<List<SensorData>> multiSourceData) {
        log.warn("JPDA融合尚未实现，退化为PDA融合");
        return probabilisticFusion(multiSourceData);
    }
}
