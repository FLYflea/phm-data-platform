package com.phm.computation.service;

import com.phm.computation.entity.SensorData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据预处理服务
 * 
 * 功能：
 * - 滑动平均去噪
 * - Z-score 标准化
 * - 3σ 准则异常值剔除
 * 
 * TODO: 可扩展
 * - 小波去噪、卡尔曼滤波等高级算法
 * - 自适应阈值异常检测
 */
@Slf4j
@Service
public class PreprocessService {

    /**
     * 滑动平均窗口大小（从配置读取，默认 5）
     */
    @Value("${preprocess.moving-average.window:5}")
    private int movingAverageWindow;

    /**
     * 滑动平均去噪
     * 
     * 算法：对时间序列进行滑动窗口平均，平滑随机噪声
     * 
     * @param values 原始数值列表
     * @return 去噪后的数值列表
     */
    public List<Double> denoise(List<Double> values) {
        if (values == null || values.isEmpty()) {
            log.warn("输入数据为空，无需去噪");
            return new ArrayList<>();
        }

        int n = values.size();
        int window = Math.min(movingAverageWindow, n);
        
        if (window <= 1) {
            log.warn("窗口大小过小，返回原始数据");
            return new ArrayList<>(values);
        }

        log.info("开始滑动平均去噪，数据点数: {}, 窗口大小: {}", n, window);

        List<Double> smoothed = new ArrayList<>();
        int halfWindow = window / 2;

        for (int i = 0; i < n; i++) {
            // 计算窗口边界
            int start = Math.max(0, i - halfWindow);
            int end = Math.min(n, i + halfWindow + 1);
            
            // 计算窗口平均值
            double sum = 0;
            int count = 0;
            for (int j = start; j < end; j++) {
                if (values.get(j) != null) {
                    sum += values.get(j);
                    count++;
                }
            }
            
            double avg = count > 0 ? sum / count : 0.0;
            smoothed.add(avg);
        }

        log.info("滑动平均去噪完成，输出 {} 个点", smoothed.size());
        return smoothed;
    }

    /**
     * Z-score 标准化
     * 
     * 公式：z = (x - μ) / σ
     * - μ：均值
     * - σ：标准差
     * 
     * @param values 原始数值列表
     * @return 标准化后的数值列表
     */
    public List<Double> normalize(List<Double> values) {
        if (values == null || values.isEmpty()) {
            log.warn("输入数据为空，无需标准化");
            return new ArrayList<>();
        }

        // 过滤 null 值
        List<Double> validValues = values.stream()
                .filter(v -> v != null)
                .collect(Collectors.toList());

        if (validValues.isEmpty()) {
            log.warn("有效数据为空，无法标准化");
            return new ArrayList<>(values);
        }

        log.info("开始 Z-score 标准化，有效数据点数: {}", validValues.size());

        // 计算均值 μ
        double mean = validValues.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        // 计算标准差 σ
        double variance = validValues.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);

        if (stdDev == 0) {
            log.warn("标准差为0，所有值相同，返回全0序列");
            return values.stream().map(v -> 0.0).collect(Collectors.toList());
        }

        // Z-score 标准化
        List<Double> normalized = new ArrayList<>();
        for (Double value : values) {
            if (value == null) {
                normalized.add(null);
            } else {
                double zScore = (value - mean) / stdDev;
                normalized.add(zScore);
            }
        }

        log.info("Z-score 标准化完成，均值: {:.4f}, 标准差: {:.4f}", mean, stdDev);
        return normalized;
    }

    /**
     * 3σ 准则剔除异常值
     * 
     * 原理：对于正态分布，99.7% 的数据落在 (μ-3σ, μ+3σ) 范围内
     * 超出此范围的数据视为异常值
     * 
     * @param data 传感器数据列表
     * @return 剔除异常值后的数据列表
     */
    public List<SensorData> removeOutliers(List<SensorData> data) {
        if (data == null || data.isEmpty()) {
            log.warn("输入数据为空，无需剔除异常值");
            return new ArrayList<>();
        }

        // 过滤掉 value 为 null 的数据
        List<SensorData> validData = data.stream()
                .filter(d -> d.getValue() != null)
                .collect(Collectors.toList());

        if (validData.size() < 3) {
            log.warn("有效数据点过少（<3），无法应用3σ准则，返回原始数据");
            return new ArrayList<>(data);
        }

        log.info("开始3σ异常值检测，数据点数: {}", validData.size());

        // 计算均值 μ
        double mean = validData.stream()
                .mapToDouble(SensorData::getValue)
                .average()
                .orElse(0.0);

        // 计算标准差 σ
        double variance = validData.stream()
                .mapToDouble(d -> Math.pow(d.getValue() - mean, 2))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);

        // 3σ 阈值
        double lowerBound = mean - 3 * stdDev;
        double upperBound = mean + 3 * stdDev;

        log.info("3σ阈值: [{:.4f}, {:.4f}]，均值: {:.4f}, 标准差: {:.4f}", 
                lowerBound, upperBound, mean, stdDev);

        // 筛选正常值
        List<SensorData> normalData = new ArrayList<>();
        List<SensorData> outliers = new ArrayList<>();

        for (SensorData item : data) {
            if (item.getValue() == null) {
                normalData.add(item); // null 值保留，不做判断
                continue;
            }

            double value = item.getValue();
            if (value >= lowerBound && value <= upperBound) {
                normalData.add(item);
            } else {
                outliers.add(item);
                log.debug("检测到异常值: deviceId={}, value={:.4f}, timestamp={}",
                        item.getDeviceId(), value, item.getTimestamp());
            }
        }

        log.info("3σ异常值检测完成，正常: {} 个，异常: {} 个", 
                normalData.size(), outliers.size());

        return normalData;
    }

    /**
     * 综合预处理管道
     * 
     * 按顺序执行：去噪 → 剔除异常值 → 标准化
     * 
     * @param data 原始传感器数据
     * @return 预处理后的数据
     */
    public List<SensorData> preprocessPipeline(List<SensorData> data) {
        log.info("开始综合预处理管道，输入 {} 条数据", data != null ? data.size() : 0);

        if (data == null || data.isEmpty()) {
            return new ArrayList<>();
        }

        // 步骤1：去噪（提取数值 → 去噪 → 回填）
        List<Double> values = data.stream()
                .map(SensorData::getValue)
                .collect(Collectors.toList());
        
        List<Double> denoisedValues = denoise(values);
        for (int i = 0; i < data.size() && i < denoisedValues.size(); i++) {
            data.get(i).setValue(denoisedValues.get(i));
        }

        // 步骤2：剔除异常值
        List<SensorData> withoutOutliers = removeOutliers(data);

        // 步骤3：标准化（提取数值 → 标准化 → 回填）
        List<Double> valuesForNorm = withoutOutliers.stream()
                .map(SensorData::getValue)
                .collect(Collectors.toList());
        
        List<Double> normalizedValues = normalize(valuesForNorm);
        for (int i = 0; i < withoutOutliers.size() && i < normalizedValues.size(); i++) {
            withoutOutliers.get(i).setValue(normalizedValues.get(i));
        }

        log.info("综合预处理管道完成，输出 {} 条数据", withoutOutliers.size());
        return withoutOutliers;
    }
}