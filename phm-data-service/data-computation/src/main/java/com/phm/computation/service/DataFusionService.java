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
 * - 最近邻关联融合（NN）- 简单平均基线
 * - 概率数据关联（PDA）- 基于高斯分布假设
 * - 联合概率数据关联（JPDA）- 枚举联合假设，边缘化权重
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
    
    // ==================== JPDA联合概率数据关联融合 ====================
    
    /**
     * JPDA（Joint Probabilistic Data Association）联合概率融合
     * 
     * 与PDA的区别：PDA对每个数据源独立计算权重；
     * JPDA枚举所有合法的"观测-目标"联合关联假设，
     * 取联合概率的边缘化结果作为最终权重。
     * 
     * 算法步骤：
     * 1. 时间窗口对齐
     * 2. 构造关联概率矩阵（每个源对目标的高斯似然）
     * 3. 枚举所有合法联合假设（每个源关联或不关联）
     * 4. 计算联合概率 = 各源概率之积
     * 5. 边缘化得到每个源的最终权重
     * 6. 加权融合
     *
     * @param multiSourceData 多源传感器数据
     * @return JPDA融合结果列表
     */
    public List<FusionResult> jpdaFusion(List<List<SensorData>> multiSourceData) {
        long startTime = System.currentTimeMillis();
        
        if (multiSourceData == null || multiSourceData.isEmpty()) {
            log.warn("JPDA融合：输入数据为空");
            return Collections.emptyList();
        }
        
        multiSourceData = multiSourceData.stream()
                .filter(list -> list != null && !list.isEmpty())
                .collect(Collectors.toList());
        
        if (multiSourceData.isEmpty()) {
            return Collections.emptyList();
        }
        
        int sourceCount = multiSourceData.size();
        log.info("开始JPDA联合概率数据关联融合，数据源数量: {}", sourceCount);
        
        List<TimeAlignedGroup> alignedGroups = alignByTimeWindow(multiSourceData, DEFAULT_TIME_WINDOW_MS);
        List<FusionResult> results = new ArrayList<>();
        
        // 用于跨时间步传递预测值（前一步的融合值）
        double lastFusedValue = Double.NaN;
        
        for (TimeAlignedGroup group : alignedGroups) {
            List<SensorData> groupData = group.getData();
            if (groupData.isEmpty()) continue;
            
            String sensorType = groupData.get(0).getSensorType();
            LocalDateTime timestamp = group.getRepresentativeTime();
            int m = groupData.size(); // 本组数据源数
            
            // 预测值：使用上一时间步融合值，首次用均值
            double predicted;
            if (Double.isNaN(lastFusedValue)) {
                predicted = groupData.stream().mapToDouble(SensorData::getValue).average().orElse(0.0);
            } else {
                predicted = lastFusedValue;
            }
            
            // 步骤2：构造关联概率矩阵 likelihoods[i] = P(观测i关联到目标)
            double[] likelihoods = new double[m];
            double[] values = new double[m];
            String[] deviceIds = new String[m];
            
            for (int i = 0; i < m; i++) {
                SensorData sd = groupData.get(i);
                values[i] = sd.getValue();
                deviceIds[i] = sd.getDeviceId();
                double error = values[i] - predicted;
                // 高斯似然
                likelihoods[i] = Math.exp(-(error * error) / (2 * JPDA_SENSOR_STD * JPDA_SENSOR_STD));
            }
            
            // 步骤3：枚举所有联合假设
            // 每个源可以"关联"(1)或"杂波/不关联"(0)，共 2^m 种假设
            // 排除全不关联的情况
            int totalHypotheses = 1 << m;
            double[] hypothesisProbs = new double[totalHypotheses];
            double totalJointProb = 0;
            
            for (int h = 1; h < totalHypotheses; h++) { // 跳过 h=0（全不关联）
                double jointProb = 1.0;
                for (int i = 0; i < m; i++) {
                    if ((h & (1 << i)) != 0) {
                        // 源i关联到目标
                        jointProb *= likelihoods[i];
                    } else {
                        // 源i视为杂波，给予一个较低的均匀概率
                        jointProb *= CLUTTER_PROBABILITY;
                    }
                }
                hypothesisProbs[h] = jointProb;
                totalJointProb += jointProb;
            }
            
            // 步骤5：边缘化 — 对每个源i，其关联概率 = 所有"源i被关联"的假设概率之和 / 总概率
            double[] marginalWeights = new double[m];
            if (totalJointProb > 0) {
                for (int i = 0; i < m; i++) {
                    double marginProb = 0;
                    for (int h = 1; h < totalHypotheses; h++) {
                        if ((h & (1 << i)) != 0) {
                            marginProb += hypothesisProbs[h];
                        }
                    }
                    marginalWeights[i] = marginProb / totalJointProb;
                }
            } else {
                // 退化为等权重
                Arrays.fill(marginalWeights, 1.0 / m);
            }
            
            // 归一化权重
            double weightSum = 0;
            for (double w : marginalWeights) weightSum += w;
            Map<String, Double> normalizedWeights = new HashMap<>();
            for (int i = 0; i < m; i++) {
                normalizedWeights.put(deviceIds[i], weightSum > 0 ? marginalWeights[i] / weightSum : 1.0 / m);
            }
            
            // 步骤6：加权融合
            double fusedValue = 0;
            for (int i = 0; i < m; i++) {
                fusedValue += normalizedWeights.get(deviceIds[i]) * values[i];
            }
            lastFusedValue = fusedValue;
            
            // 方差计算
            List<Double> valueList = new ArrayList<>();
            for (double v : values) valueList.add(v);
            double varianceBefore = calculateVariance(valueList);
            double varianceAfter = 0;
            for (int i = 0; i < m; i++) {
                double w = normalizedWeights.get(deviceIds[i]);
                varianceAfter += w * Math.pow(values[i] - fusedValue, 2);
            }
            double varianceReduction = varianceBefore > 0 ? (varianceBefore - varianceAfter) / varianceBefore : 0;
            
            double confidence = calculateConfidence(normalizedWeights);
            
            FusionResult result = new FusionResult();
            result.setFusedSensorType(sensorType);
            result.setFusedValue(fusedValue);
            result.setTimestamp(timestamp);
            result.setSourceWeights(normalizedWeights);
            result.setConfidence(confidence);
            result.setFusionMethod("JPDA");
            result.setSourceCount(m);
            result.setVarianceBefore(varianceBefore);
            result.setVarianceAfter(varianceAfter);
            result.setVarianceReduction(varianceReduction);
            result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            result.setNote(String.format("JPDA融合: %d个数据源, 枚举%d种假设, 方差缩减%.2f%%", 
                    m, totalHypotheses - 1, varianceReduction * 100));
            
            results.add(result);
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        log.info("JPDA联合概率融合完成，输出 {} 条结果，总耗时: {}ms", results.size(), totalTime);
        return results;
    }
    
    /**
     * JPDA 参数：传感器噪声标准差（略大于PDA，更宽松的关联门限）
     */
    private static final double JPDA_SENSOR_STD = 0.15;
    
    /**
     * JPDA 参数：杂波/不关联假设的均匀概率
     */
    private static final double CLUTTER_PROBABILITY = 0.1;
}
