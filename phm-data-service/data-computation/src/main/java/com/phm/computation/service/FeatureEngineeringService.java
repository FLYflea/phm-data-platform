package com.phm.computation.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 特征工程服务
 *
 * 功能：
 * - 时域特征提取（均值、方差、标准差、峰值、峰峰值等）
 * - 频域特征提取（基于 DFT 简化版，提取主频率成分）
 * - 时频域特征接口预留（小波变换待实现）
 *
 * TODO: 可扩展
 * - 频域：引入 Apache Commons Math 的完整 FFT 实现
 * - 时频域：接入 JWave 等小波变换库
 * - 可接入深度学习特征提取（自编码器）
 */
@Slf4j
@Service
public class FeatureEngineeringService {

    // ==================== 时域特征 ====================

    /**
     * 提取时域特征
     *
     * 包含：均值、方差、标准差、均方根、峰值、峰峰值、波形指标、峭度
     *
     * @param values 原始数值列表
     * @return 时域特征 Map<特征名, 特征值>
     */
    public Map<String, Double> extractTimeDomain(List<Double> values) {
        if (values == null || values.isEmpty()) {
            log.warn("输入数据为空，无法提取时域特征");
            return Collections.emptyMap();
        }

        // 过滤 null 值
        List<Double> valid = values.stream()
                .filter(v -> v != null)
                .collect(Collectors.toList());

        if (valid.isEmpty()) {
            log.warn("有效数据为空，无法提取时域特征");
            return Collections.emptyMap();
        }

        log.info("开始提取时域特征，数据点数: {}", valid.size());

        Map<String, Double> features = new LinkedHashMap<>();

        // 1. 均值 (Mean)
        double mean = valid.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        features.put("mean", mean);

        // 2. 方差 (Variance)
        double variance = valid.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
        features.put("variance", variance);

        // 3. 标准差 (Standard Deviation)
        double stdDev = Math.sqrt(variance);
        features.put("stdDev", stdDev);

        // 4. 均方根 (RMS - Root Mean Square)
        double rms = Math.sqrt(valid.stream()
                .mapToDouble(v -> v * v)
                .average()
                .orElse(0.0));
        features.put("rms", rms);

        // 5. 峰值 (Peak) - 绝对值最大
        double peak = valid.stream()
                .mapToDouble(v -> Math.abs(v))
                .max()
                .orElse(0.0);
        features.put("peak", peak);

        // 6. 最大值 / 最小值
        double max = valid.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double min = valid.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        features.put("max", max);
        features.put("min", min);

        // 7. 峰峰值 (Peak-to-Peak)
        double peakToPeak = max - min;
        features.put("peakToPeak", peakToPeak);

        // 8. 波形指标 (Waveform Index) = RMS / 绝对均值
        double absMean = valid.stream().mapToDouble(v -> Math.abs(v)).average().orElse(0.0);
        double waveformIndex = absMean > 0 ? rms / absmean(valid) : 0.0;
        features.put("waveformIndex", waveformIndex);

        // 9. 峰值指标 (Crest Factor) = Peak / RMS
        double crestFactor = rms > 0 ? peak / rms : 0.0;
        features.put("crestFactor", crestFactor);

        // 10. 峭度 (Kurtosis)
        double kurtosis = 0.0;
        if (stdDev > 0) {
            kurtosis = valid.stream()
                    .mapToDouble(v -> Math.pow((v - mean) / stdDev, 4))
                    .average()
                    .orElse(0.0);
        }
        features.put("kurtosis", kurtosis);

        // 11. 偏度 (Skewness)
        double skewness = 0.0;
        if (stdDev > 0) {
            skewness = valid.stream()
                    .mapToDouble(v -> Math.pow((v - mean) / stdDev, 3))
                    .average()
                    .orElse(0.0);
        }
        features.put("skewness", skewness);

        log.info("时域特征提取完成，共 {} 个特征", features.size());
        return features;
    }

    // ==================== 频域特征 ====================

    /**
     * 提取频域特征（DFT 简化版）
     *
     * 基于离散傅里叶变换（DFT）计算幅值谱，提取主频率成分
     *
     * TODO: 可扩展
     * - 引入 Apache Commons Math FFT 提升精度和效率（O(n log n)）
     * - 当前为 O(n²) 的 DFT，适合小数据量
     *
     * @param values 原始数值列表
     * @return 频域特征 Map，包含主频率、主频幅值、频带能量等
     */
    public Map<String, Object> extractFrequencyDomain(List<Double> values) {
        if (values == null || values.size() < 4) {
            log.warn("数据点数不足（需要至少4个点），无法提取频域特征");
            return Collections.emptyMap();
        }

        List<Double> valid = values.stream()
                .filter(v -> v != null)
                .collect(Collectors.toList());

        int n = valid.size();
        log.info("开始提取频域特征（DFT简化版），数据点数: {}", n);

        Map<String, Object> features = new LinkedHashMap<>();

        // 计算 DFT 幅值谱（只计算正频率部分）
        int halfN = n / 2;
        double[] amplitudes = new double[halfN];
        double[] frequencies = new double[halfN];

        for (int k = 0; k < halfN; k++) {
            double realPart = 0.0;
            double imagPart = 0.0;
            for (int t = 0; t < n; t++) {
                double angle = 2 * Math.PI * k * t / n;
                realPart += valid.get(t) * Math.cos(angle);
                imagPart -= valid.get(t) * Math.sin(angle);
            }
            amplitudes[k] = Math.sqrt(realPart * realPart + imagPart * imagPart) / n;
            frequencies[k] = (double) k; // 归一化频率索引
        }

        // 找到主频率（幅值最大的频率分量，跳过直流分量 k=0）
        int dominantIdx = 1;
        for (int k = 2; k < halfN; k++) {
            if (amplitudes[k] > amplitudes[dominantIdx]) {
                dominantIdx = k;
            }
        }

        features.put("dominantFrequencyIndex", frequencies[dominantIdx]);
        features.put("dominantAmplitude", amplitudes[dominantIdx]);
        features.put("dcComponent", amplitudes[0]); // 直流分量

        // 频带能量（低频 / 中频 / 高频各占 1/3）
        double lowEnergy = 0, midEnergy = 0, highEnergy = 0;
        int third = halfN / 3;
        for (int k = 1; k < halfN; k++) {
            double e = amplitudes[k] * amplitudes[k];
            if (k < third) {
                lowEnergy += e;
            } else if (k < 2 * third) {
                midEnergy += e;
            } else {
                highEnergy += e;
            }
        }
        double totalEnergy = lowEnergy + midEnergy + highEnergy;

        features.put("lowBandEnergy", lowEnergy);
        features.put("midBandEnergy", midEnergy);
        features.put("highBandEnergy", highEnergy);
        features.put("totalSpectralEnergy", totalEnergy);
        features.put("lowBandRatio", totalEnergy > 0 ? lowEnergy / totalEnergy : 0.0);
        features.put("highBandRatio", totalEnergy > 0 ? highEnergy / totalEnergy : 0.0);

        // Top-3 主频幅值
        List<double[]> topFreqs = getTopNFrequencies(amplitudes, 3);
        features.put("top3Frequencies", topFreqs.stream()
                .map(f -> Map.of("index", f[0], "amplitude", f[1]))
                .collect(Collectors.toList()));

        log.info("频域特征提取完成，主频索引: {}, 幅值: {:.4f}", dominantIdx, amplitudes[dominantIdx]);
        return features;
    }

    // ==================== 时频域特征（接口预留） ====================

    /**
     * 提取时频域特征（接口预留）
     *
     * TODO: 小波变换待实现
     * - 计划接入连续小波变换（CWT）或离散小波变换（DWT）
     * - 可选库：JWave、Apache Commons Math
     * - 时频域特征适用于非平稳信号分析（如机械冲击）
     *
     * @param values 原始数值列表
     * @return 时频域特征（当前返回空 Map）
     */
    public Map<String, Object> extractTimeFrequency(List<Double> values) {
        log.info("时频域特征提取接口已调用，数据点数: {}，小波变换待实现", 
                values != null ? values.size() : 0);

        // TODO: 小波变换待实现
        // 实现思路：
        // 1. 选择母小波（Morlet / Daubechies / Haar）
        // 2. 计算小波系数矩阵（时间 × 尺度）
        // 3. 提取各频带的小波能量
        // 4. 计算时频局部化特征

        Map<String, Object> placeholder = new LinkedHashMap<>();
        placeholder.put("status", "待实现");
        placeholder.put("note", "小波变换待实现，当前返回空特征");
        placeholder.put("plannedFeatures", Arrays.asList(
                "waveletEnergy",
                "waveletEntropy",
                "timeFrequencyMean",
                "instantaneousFrequency"
        ));

        return placeholder;
    }

    // ==================== 综合特征提取 ====================

    /**
     * 提取全部特征（时域 + 频域）
     *
     * @param values 原始数值列表
     * @return 全部特征的汇总 Map
     */
    public Map<String, Object> extractAllFeatures(List<Double> values) {
        log.info("开始提取全部特征，数据点数: {}", values != null ? values.size() : 0);

        Map<String, Object> allFeatures = new LinkedHashMap<>();

        Map<String, Double> timeDomain = extractTimeDomain(values);
        allFeatures.put("timeDomain", timeDomain);

        Map<String, Object> freqDomain = extractFrequencyDomain(values);
        allFeatures.put("frequencyDomain", freqDomain);

        Map<String, Object> timeFreq = extractTimeFrequency(values);
        allFeatures.put("timeFrequency", timeFreq);

        log.info("全部特征提取完成，时域 {} 个，频域 {} 个", timeDomain.size(), freqDomain.size());
        return allFeatures;
    }

    // ==================== 私有方法 ====================

    /**
     * 计算绝对均值
     */
    private double absmean(List<Double> values) {
        return values.stream()
                .mapToDouble(v -> Math.abs(v))
                .average()
                .orElse(1.0);
    }

    /**
     * 获取幅值最大的前 N 个频率分量
     *
     * @param amplitudes 幅值数组
     * @param n          取前 n 个
     * @return List<double[]>，每个元素为 [频率索引, 幅值]
     */
    private List<double[]> getTopNFrequencies(double[] amplitudes, int n) {
        List<double[]> list = new ArrayList<>();
        for (int k = 1; k < amplitudes.length; k++) {
            list.add(new double[]{k, amplitudes[k]});
        }
        list.sort((a, b) -> Double.compare(b[1], a[1]));
        return list.subList(0, Math.min(n, list.size()));
    }
}
