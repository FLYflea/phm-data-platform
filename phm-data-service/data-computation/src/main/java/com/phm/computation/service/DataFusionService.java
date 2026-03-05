package com.phm.computation.service;

import com.phm.computation.entity.SensorData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 多源数据融合服务
 *
 * 功能：
 * - 最近邻关联融合（NN）
 * - 概率数据关联（PDA）- 接口预留
 * - 联合概率数据关联（JPDA）- 接口预留
 *
 * TODO: NN/PDA/JPDA 待实现
 * - 当前 NN 为简化版（时间窗口匹配 + 加权平均）
 * - PDA/JPDA 为接口预留，模拟返回
 */
@Slf4j
@Service
public class DataFusionService {

    /**
     * 默认时间窗口（毫秒），用于最近邻匹配
     */
    private static final long DEFAULT_TIME_WINDOW_MS = 500;

    /**
     * 融合结果数据结构
     */
    @lombok.Data
    public static class FusionResult {
        /** 融合后的传感器类型标识 */
        private String fusedSensorType;
        /** 融合后的数值（加权平均） */
        private Double fusedValue;
        /** 融合时间戳（最早时间戳） */
        private java.time.LocalDateTime timestamp;
        /** 参与融合的数据源个数 */
        private int sourceCount;
        /** 融合算法 */
        private String algorithm;
        /** 备注 */
        private String note;
    }

    // ==================== 最近邻关联（NN） ====================

    /**
     * 最近邻关联融合（简化实现）
     *
     * 实现策略：
     * 1. 按 sensorType 分组
     * 2. 在时间窗口内寻找最近邻数据点
     * 3. 对同窗口内数据做加权平均（权重 = 1/时间距离）
     *
     * TODO: NN 待实现完整版
     * - 当前简化为时间窗口分组聚合
     * - 完整 NN 应维护航迹（Track），对多目标做代价矩阵匹配
     * - 可使用匈牙利算法求解最优分配
     *
     * @param sources 多源传感器数据（可来自不同设备/传感器类型）
     * @return 融合后的结果列表
     */
    public List<FusionResult> nearestNeighborFusion(List<SensorData> sources) {
        if (sources == null || sources.isEmpty()) {
            log.warn("NN融合：输入数据为空");
            return Collections.emptyList();
        }

        log.info("开始最近邻关联融合，数据条数: {}", sources.size());

        // 过滤无效数据
        List<SensorData> valid = sources.stream()
                .filter(d -> d.getTimestamp() != null && d.getValue() != null)
                .sorted(Comparator.comparing(SensorData::getTimestamp))
                .collect(Collectors.toList());

        if (valid.isEmpty()) {
            log.warn("NN融合：无有效数据");
            return Collections.emptyList();
        }

        List<FusionResult> results = new ArrayList<>();

        // 按 sensorType 分组，逐组进行时间窗口融合
        Map<String, List<SensorData>> byType = valid.stream()
                .collect(Collectors.groupingBy(SensorData::getSensorType));

        for (Map.Entry<String, List<SensorData>> entry : byType.entrySet()) {
            String sensorType = entry.getKey();
            List<SensorData> typeData = entry.getValue();

            // 在时间窗口内分组
            List<List<SensorData>> windows = splitByTimeWindow(typeData, DEFAULT_TIME_WINDOW_MS);

            for (List<SensorData> window : windows) {
                FusionResult result = fuseWindowByWeightedAverage(window, sensorType, "NearestNeighbor");
                results.add(result);
            }
        }

        log.info("最近邻关联融合完成，输出 {} 条融合结果", results.size());
        return results;
    }

    // ==================== 概率数据关联（PDA）- 接口预留 ====================

    /**
     * 概率数据关联融合（接口预留）
     *
     * TODO: PDA 待实现
     * - PDA 假设量测来自目标或杂波，计算每个量测属于目标的概率
     * - 融合时以概率为权重对所有候选量测加权求和
     * - 需要：目标模型、传感器噪声模型、杂波密度参数
     * - 适用场景：单目标跟踪、低杂波密度环境
     *
     * @param sources 多源传感器数据
     * @return 融合结果（当前模拟返回）
     */
    public List<FusionResult> probabilisticFusion(List<SensorData> sources) {
        if (sources == null || sources.isEmpty()) {
            log.warn("PDA融合：输入数据为空");
            return Collections.emptyList();
        }

        log.info("PDA融合接口调用，数据条数: {}，当前为模拟返回", sources.size());

        // TODO: PDA 待实现
        // 实现步骤：
        // 1. 建立目标状态预测（卡尔曼预测步）
        // 2. 计算候选量测的关联概率（波门内所有量测）
        // 3. 按概率加权求和得到综合新息
        // 4. 卡尔曼更新步

        // 当前：退化为简单加权平均（模拟）
        List<SensorData> valid = sources.stream()
                .filter(d -> d.getTimestamp() != null && d.getValue() != null)
                .collect(Collectors.toList());

        if (valid.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, List<SensorData>> byType = valid.stream()
                .collect(Collectors.groupingBy(SensorData::getSensorType));

        List<FusionResult> results = new ArrayList<>();
        for (Map.Entry<String, List<SensorData>> entry : byType.entrySet()) {
            List<List<SensorData>> windows = splitByTimeWindow(entry.getValue(), DEFAULT_TIME_WINDOW_MS);
            for (List<SensorData> window : windows) {
                FusionResult result = fuseWindowByWeightedAverage(window, entry.getKey(), "PDA(模拟)");
                result.setNote("PDA待实现：当前为加权平均模拟，需接入卡尔曼滤波和概率关联");
                results.add(result);
            }
        }

        log.info("PDA融合（模拟）完成，输出 {} 条结果", results.size());
        return results;
    }

    // ==================== 联合概率数据关联（JPDA）- 接口预留 ====================

    /**
     * 联合概率数据关联融合（接口预留）
     *
     * TODO: JPDA 待实现
     * - JPDA 是 PDA 的多目标扩展，考虑量测在多目标间的联合分配概率
     * - 计算所有可行分配的联合概率，解决多目标关联歧义
     * - 计算复杂度高，大规模目标下需近似方法（如 CJPDA）
     * - 适用场景：多目标跟踪、密集目标、高杂波环境
     *
     * @param sources 多源传感器数据
     * @return 融合结果（当前模拟返回）
     */
    public List<FusionResult> jpdaFusion(List<SensorData> sources) {
        if (sources == null || sources.isEmpty()) {
            log.warn("JPDA融合：输入数据为空");
            return Collections.emptyList();
        }

        log.info("JPDA融合接口调用，数据条数: {}，当前为模拟返回", sources.size());

        // TODO: JPDA 待实现
        // 实现步骤：
        // 1. 波门检测，确定候选量测集合
        // 2. 枚举所有可行关联事件（量测-目标联合分配）
        // 3. 计算每个联合事件的概率
        // 4. 计算边缘关联概率（JPDA系数）
        // 5. 以 JPDA 系数为权重更新每个目标状态

        List<SensorData> valid = sources.stream()
                .filter(d -> d.getTimestamp() != null && d.getValue() != null)
                .collect(Collectors.toList());

        if (valid.isEmpty()) {
            return Collections.emptyList();
        }

        // 当前：退化为多目标分组融合（模拟）
        Map<String, List<SensorData>> byDevice = valid.stream()
                .collect(Collectors.groupingBy(SensorData::getDeviceId));

        List<FusionResult> results = new ArrayList<>();
        for (Map.Entry<String, List<SensorData>> deviceEntry : byDevice.entrySet()) {
            Map<String, List<SensorData>> byType = deviceEntry.getValue().stream()
                    .collect(Collectors.groupingBy(SensorData::getSensorType));

            for (Map.Entry<String, List<SensorData>> typeEntry : byType.entrySet()) {
                List<List<SensorData>> windows = splitByTimeWindow(typeEntry.getValue(), DEFAULT_TIME_WINDOW_MS);
                for (List<SensorData> window : windows) {
                    FusionResult result = fuseWindowByWeightedAverage(window, typeEntry.getKey(), "JPDA(模拟)");
                    result.setNote("JPDA待实现：当前为多目标分组模拟，需接入联合概率关联算法");
                    results.add(result);
                }
            }
        }

        log.info("JPDA融合（模拟）完成，输出 {} 条结果", results.size());
        return results;
    }

    // ==================== 私有方法 ====================

    /**
     * 按时间窗口切分数据
     *
     * @param data          已按时间排序的数据
     * @param windowMs      窗口大小（毫秒）
     * @return 切分后的子列表
     */
    private List<List<SensorData>> splitByTimeWindow(List<SensorData> data, long windowMs) {
        List<List<SensorData>> windows = new ArrayList<>();
        if (data.isEmpty()) return windows;

        List<SensorData> currentWindow = new ArrayList<>();
        currentWindow.add(data.get(0));

        for (int i = 1; i < data.size(); i++) {
            SensorData prev = data.get(i - 1);
            SensorData curr = data.get(i);
            long diffMs = ChronoUnit.MILLIS.between(prev.getTimestamp(), curr.getTimestamp());

            if (diffMs <= windowMs) {
                currentWindow.add(curr);
            } else {
                windows.add(new ArrayList<>(currentWindow));
                currentWindow.clear();
                currentWindow.add(curr);
            }
        }

        if (!currentWindow.isEmpty()) {
            windows.add(currentWindow);
        }

        return windows;
    }

    /**
     * 对窗口内数据做加权平均融合
     *
     * 权重策略：数据越新（时间越靠后）权重越大
     *
     * @param window      窗口内数据列表
     * @param sensorType  传感器类型标识
     * @param algorithm   算法名称
     * @return 融合结果
     */
    private FusionResult fuseWindowByWeightedAverage(List<SensorData> window,
                                                      String sensorType,
                                                      String algorithm) {
        int n = window.size();
        double weightedSum = 0.0;
        double totalWeight = 0.0;

        for (int i = 0; i < n; i++) {
            // 时间越靠后，权重越大（线性递增）
            double weight = i + 1.0;
            weightedSum += window.get(i).getValue() * weight;
            totalWeight += weight;
        }

        double fusedValue = totalWeight > 0 ? weightedSum / totalWeight : 0.0;

        FusionResult result = new FusionResult();
        result.setFusedSensorType(sensorType);
        result.setFusedValue(fusedValue);
        result.setTimestamp(window.get(0).getTimestamp());
        result.setSourceCount(n);
        result.setAlgorithm(algorithm);
        result.setNote(String.format("窗口内 %d 个数据点线性加权平均融合", n));

        return result;
    }
}
