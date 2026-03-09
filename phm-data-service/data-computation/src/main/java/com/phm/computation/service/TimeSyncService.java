package com.phm.computation.service;

import com.phm.computation.entity.SensorData;
import com.phm.computation.entity.SyncedData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 时间同步服务
 * 
 * 功能：
 * - 多源传感器数据的时间戳对齐与排序
 * - 基于不确定性理论的同步算法（隶属度迭代更新）
 * - 采样率对齐（上采样/下采样）
 * 
 * TODO: 已完成不确定性同步算法实现 2026-03-09
 * - 实现了基于隶属度的概率同步算法
 * - 支持k邻居加权迭代更新
 * - 低概率点插值修复
 * - 采样率对齐支持聚合和插值两种方法
 */
@Slf4j
@Service
public class TimeSyncService {

    /**
     * 默认收敛阈值
     */
    private static final double CONVERGENCE_THRESHOLD = 0.001;
    
    /**
     * 最大迭代次数
     */
    private static final int MAX_ITERATIONS = 10;
    
    /**
     * 低概率阈值（低于此值需要插值修复）
     */
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.5;

    /**
     * 不确定性同步算法（核心算法）
     * 
     * 算法步骤：
     * 1. 按原始时间戳排序
     * 2. 计算初始隶属概率（基于与期望采样率的偏差）
     * 3. 迭代更新：根据前后k个邻居的概率加权调整
     * 4. 收敛判断：变化<0.001或最多10轮
     * 5. 低概率点插值修复
     * 
     * @param rawData 原始传感器数据列表
     * @param kNeighbors 邻居数量k
     * @param expectedIntervalMs 期望采样间隔（毫秒）
     * @return 同步后的数据列表
     */
    public List<SyncedData> uncertaintySync(List<SensorData> rawData, int kNeighbors, long expectedIntervalMs) {
        long startTime = System.currentTimeMillis();
        
        if (rawData == null || rawData.isEmpty()) {
            log.warn("不确定性同步：输入数据为空");
            return Collections.emptyList();
        }
        
        log.info("开始不确定性同步，数据条数: {}, k邻居: {}, 期望间隔: {}ms", 
                rawData.size(), kNeighbors, expectedIntervalMs);
        
        // 步骤1：按原始时间戳排序
        List<SensorData> sortedData = rawData.stream()
                .filter(d -> d.getTimestamp() != null && d.getValue() != null)
                .sorted(Comparator.comparing(SensorData::getTimestamp))
                .collect(Collectors.toList());
        
        if (sortedData.isEmpty()) {
            log.warn("不确定性同步：无有效数据");
            return Collections.emptyList();
        }
        
        int n = sortedData.size();
        
        // 步骤2：计算初始隶属概率
        // 公式：隶属度 = 1 / (1 + |实际间隔-期望间隔|/期望间隔)
        double[] probabilities = new double[n];
        probabilities[0] = 1.0; // 第一个点概率为1
        
        for (int i = 1; i < n; i++) {
            long actualInterval = ChronoUnit.MILLIS.between(
                    sortedData.get(i - 1).getTimestamp(),
                    sortedData.get(i).getTimestamp()
            );
            double deviation = Math.abs(actualInterval - expectedIntervalMs) / (double) expectedIntervalMs;
            probabilities[i] = 1.0 / (1.0 + deviation);
        }
        
        log.debug("初始隶属概率计算完成，范围: [{}, {}]", 
                Arrays.stream(probabilities).min().orElse(0),
                Arrays.stream(probabilities).max().orElse(1));
        
        // 步骤3&4：迭代更新概率直到收敛
        double[] newProbabilities = new double[n];
        int iteration = 0;
        double maxChange = Double.MAX_VALUE;
        
        while (maxChange > CONVERGENCE_THRESHOLD && iteration < MAX_ITERATIONS) {
            System.arraycopy(probabilities, 0, newProbabilities, 0, n);
            maxChange = 0.0;
            
            for (int i = 0; i < n; i++) {
                // 获取前后k个邻居
                double weightedSum = probabilities[i]; // 自身权重为1
                double totalWeight = 1.0;
                
                // 前向邻居
                for (int j = 1; j <= kNeighbors && i - j >= 0; j++) {
                    double distance = j; // 距离 = 邻居序号
                    double weight = 1.0 / distance;
                    weightedSum += probabilities[i - j] * weight;
                    totalWeight += weight;
                }
                
                // 后向邻居
                for (int j = 1; j <= kNeighbors && i + j < n; j++) {
                    double distance = j;
                    double weight = 1.0 / distance;
                    weightedSum += probabilities[i + j] * weight;
                    totalWeight += weight;
                }
                
                newProbabilities[i] = weightedSum / totalWeight;
                double change = Math.abs(newProbabilities[i] - probabilities[i]);
                if (change > maxChange) {
                    maxChange = change;
                }
            }
            
            System.arraycopy(newProbabilities, 0, probabilities, 0, n);
            iteration++;
            log.debug("迭代 {}: 最大变化 = {}", iteration, maxChange);
        }
        
        log.info("不确定性同步迭代完成，共 {} 轮，最终收敛变化: {}", iteration, maxChange);
        
        // 步骤5：构建同步结果，低概率点用插值修复
        List<SyncedData> syncedDataList = new ArrayList<>();
        String deviceId = sortedData.get(0).getDeviceId();
        String sensorType = sortedData.get(0).getSensorType();
        
        for (int i = 0; i < n; i++) {
            double confidence = probabilities[i];
            SensorData original = sortedData.get(i);
            
            SyncedData synced = new SyncedData();
            synced.setDeviceId(deviceId);
            synced.setSensorType(sensorType);
            synced.setOriginalTimestamp(original.getTimestamp());
            synced.setConfidence(confidence);
            synced.setSyncMethod("uncertainty");
            synced.setInterpolated(false);
            
            // 步骤5：低概率点插值修复
            if (confidence < LOW_CONFIDENCE_THRESHOLD && i > 0 && i < n - 1) {
                // 使用线性插值
                SensorData prev = sortedData.get(i - 1);
                SensorData next = sortedData.get(i + 1);
                
                double interpolatedValue = (prev.getValue() + next.getValue()) / 2.0;
                LocalDateTime interpolatedTime = prev.getTimestamp()
                        .plus(ChronoUnit.MILLIS.between(prev.getTimestamp(), next.getTimestamp()) / 2, ChronoUnit.MILLIS);
                
                synced.setValue(interpolatedValue);
                synced.setSyncedTimestamp(interpolatedTime);
                synced.setInterpolated(true);
                
                log.debug("数据点 {} 置信度低({})，已插值修复", i, String.format("%.3f", confidence));
            } else {
                synced.setValue(original.getValue());
                synced.setSyncedTimestamp(original.getTimestamp());
            }
            
            syncedDataList.add(synced);
        }
        
        long processingTime = System.currentTimeMillis() - startTime;
        log.info("不确定性同步完成，输出 {} 条数据，处理耗时: {}ms", syncedDataList.size(), processingTime);
        
        return syncedDataList;
    }
    
    /**
     * 采样率对齐（增强版）
     * 
     * @param data 传感器数据列表（已排序）
     * @param targetRateHz 目标采样率（Hz）
     * @param method 对齐方法："aggregation"（聚合）或 "interpolation"（插值）
     * @return 对齐后的同步数据列表
     */
    public List<SyncedData> alignSamplingRate(List<SensorData> data, int targetRateHz, String method) {
        long startTime = System.currentTimeMillis();
        
        if (data == null || data.isEmpty()) {
            log.warn("采样率对齐：输入数据为空");
            return Collections.emptyList();
        }
        
        log.info("开始采样率对齐，数据条数: {}, 目标采样率: {}Hz, 方法: {}", 
                data.size(), targetRateHz, method);
        
        // 先按时间排序
        List<SensorData> sortedData = data.stream()
                .filter(d -> d.getTimestamp() != null && d.getValue() != null)
                .sorted(Comparator.comparing(SensorData::getTimestamp))
                .collect(Collectors.toList());
        
        if (sortedData.size() < 2) {
            log.warn("采样率对齐：数据点不足");
            return convertToSyncedData(sortedData, "align");
        }
        
        // 计算目标采样间隔（毫秒）
        long targetIntervalMs = 1000 / targetRateHz;
        
        // 估算原始采样率
        long originalIntervalMs = estimateSamplingInterval(sortedData);
        log.info("原始采样间隔: {}ms, 目标采样间隔: {}ms", originalIntervalMs, targetIntervalMs);
        
        List<SyncedData> result;
        if (originalIntervalMs > targetIntervalMs) {
            // 原始采样率低（间隔大）→ 需要上采样（插值）
            log.info("执行上采样：{}ms → {}ms", originalIntervalMs, targetIntervalMs);
            if ("aggregation".equals(method)) {
                // 虽然需要上采样，但用户指定了聚合，使用默认插值
                log.warn("上采样场景下aggregation方法不适用，使用interpolation");
            }
            result = upsampleToSyncedData(sortedData, targetIntervalMs);
        } else if (originalIntervalMs < targetIntervalMs) {
            // 原始采样率高（间隔小）→ 需要下采样（聚合）
            log.info("执行下采样：{}ms → {}ms", originalIntervalMs, targetIntervalMs);
            if ("interpolation".equals(method)) {
                // 虽然需要下采样，但用户指定了插值，使用默认聚合
                log.warn("下采样场景下interpolation方法不适用，使用aggregation");
            }
            result = downsampleToSyncedData(sortedData, targetIntervalMs);
        } else {
            // 采样率相同，直接转换
            log.info("采样率相同，直接转换");
            result = convertToSyncedData(sortedData, "align");
        }
        
        long processingTime = System.currentTimeMillis() - startTime;
        log.info("采样率对齐完成，输出 {} 条数据，处理耗时: {}ms", result.size(), processingTime);
        
        return result;
    }
    
    /**
     * 下采样：高采样率 → 窗口聚合
     */
    private List<SyncedData> downsampleToSyncedData(List<SensorData> dataList, long targetIntervalMs) {
        List<SyncedData> result = new ArrayList<>();
        
        if (dataList.isEmpty()) return result;
        
        LocalDateTime startTime = dataList.get(0).getTimestamp();
        LocalDateTime currentWindowStart = startTime;
        LocalDateTime currentWindowEnd = startTime.plus(targetIntervalMs, ChronoUnit.MILLIS);
        
        List<SensorData> windowData = new ArrayList<>();
        String deviceId = dataList.get(0).getDeviceId();
        String sensorType = dataList.get(0).getSensorType();
        
        for (SensorData data : dataList) {
            LocalDateTime timestamp = data.getTimestamp();
            
            if (timestamp.isBefore(currentWindowEnd) || timestamp.isEqual(currentWindowEnd)) {
                windowData.add(data);
            } else {
                // 窗口结束，计算聚合值
                if (!windowData.isEmpty()) {
                    SyncedData aggregated = aggregateWindowToSyncedData(
                            windowData, currentWindowStart, deviceId, sensorType);
                    aggregated.setSyncMethod("aggregation");
                    result.add(aggregated);
                }
                
                // 移动窗口
                while (timestamp.isAfter(currentWindowEnd)) {
                    currentWindowStart = currentWindowEnd;
                    currentWindowEnd = currentWindowStart.plus(targetIntervalMs, ChronoUnit.MILLIS);
                }
                
                windowData.clear();
                windowData.add(data);
            }
        }
        
        // 处理最后一个窗口
        if (!windowData.isEmpty()) {
            SyncedData aggregated = aggregateWindowToSyncedData(
                    windowData, currentWindowStart, deviceId, sensorType);
            aggregated.setSyncMethod("aggregation");
            result.add(aggregated);
        }
        
        return result;
    }
    
    /**
     * 上采样：低采样率 → 线性插值填充
     */
    private List<SyncedData> upsampleToSyncedData(List<SensorData> dataList, long targetIntervalMs) {
        List<SyncedData> result = new ArrayList<>();
        
        if (dataList.size() < 2) {
            return convertToSyncedData(dataList, "interpolation");
        }
        
        String deviceId = dataList.get(0).getDeviceId();
        String sensorType = dataList.get(0).getSensorType();
        
        for (int i = 0; i < dataList.size() - 1; i++) {
            SensorData current = dataList.get(i);
            SensorData next = dataList.get(i + 1);
            
            LocalDateTime currentTime = current.getTimestamp();
            LocalDateTime nextTime = next.getTimestamp();
            double currentValue = current.getValue();
            double nextValue = next.getValue();
            
            // 计算两个点之间需要插入多少个点
            long intervalMs = ChronoUnit.MILLIS.between(currentTime, nextTime);
            int numPoints = (int) (intervalMs / targetIntervalMs);
            
            // 添加当前点
            SyncedData syncedCurrent = new SyncedData();
            syncedCurrent.setDeviceId(deviceId);
            syncedCurrent.setSensorType(sensorType);
            syncedCurrent.setOriginalTimestamp(currentTime);
            syncedCurrent.setSyncedTimestamp(currentTime);
            syncedCurrent.setValue(currentValue);
            syncedCurrent.setConfidence(1.0);
            syncedCurrent.setSyncMethod("interpolation");
            syncedCurrent.setInterpolated(false);
            result.add(syncedCurrent);
            
            // 线性插值填充
            for (int j = 1; j < numPoints; j++) {
                double ratio = (double) j / numPoints;
                double interpolatedValue = currentValue + (nextValue - currentValue) * ratio;
                LocalDateTime interpolatedTime = currentTime.plus(j * targetIntervalMs, ChronoUnit.MILLIS);
                
                SyncedData interpolated = new SyncedData();
                interpolated.setDeviceId(deviceId);
                interpolated.setSensorType(sensorType);
                interpolated.setOriginalTimestamp(interpolatedTime);
                interpolated.setSyncedTimestamp(interpolatedTime);
                interpolated.setValue(interpolatedValue);
                interpolated.setConfidence(0.8); // 插值点置信度略低
                interpolated.setSyncMethod("interpolation");
                interpolated.setInterpolated(true);
                result.add(interpolated);
            }
        }
        
        // 添加最后一个点
        SensorData last = dataList.get(dataList.size() - 1);
        SyncedData syncedLast = new SyncedData();
        syncedLast.setDeviceId(deviceId);
        syncedLast.setSensorType(sensorType);
        syncedLast.setOriginalTimestamp(last.getTimestamp());
        syncedLast.setSyncedTimestamp(last.getTimestamp());
        syncedLast.setValue(last.getValue());
        syncedLast.setConfidence(1.0);
        syncedLast.setSyncMethod("interpolation");
        syncedLast.setInterpolated(false);
        result.add(syncedLast);
        
        return result;
    }
    
    /**
     * 聚合窗口数据
     */
    private SyncedData aggregateWindowToSyncedData(List<SensorData> windowData, LocalDateTime windowTime,
                                                    String deviceId, String sensorType) {
        double avgValue = windowData.stream()
                .mapToDouble(SensorData::getValue)
                .average()
                .orElse(0.0);
        
        // 计算窗口内数据的标准差作为置信度（标准差越小置信度越高）
        double variance = windowData.stream()
                .mapToDouble(d -> Math.pow(d.getValue() - avgValue, 2))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);
        double confidence = Math.max(0.5, 1.0 - stdDev / (Math.abs(avgValue) + 1e-6));
        
        SyncedData aggregated = new SyncedData();
        aggregated.setDeviceId(deviceId);
        aggregated.setSensorType(sensorType);
        aggregated.setOriginalTimestamp(windowTime);
        aggregated.setSyncedTimestamp(windowTime);
        aggregated.setValue(avgValue);
        aggregated.setConfidence(confidence);
        aggregated.setInterpolated(false);
        
        return aggregated;
    }
    
    /**
     * 转换为SyncedData列表
     */
    private List<SyncedData> convertToSyncedData(List<SensorData> dataList, String method) {
        return dataList.stream().map(d -> {
            SyncedData synced = new SyncedData();
            synced.setDeviceId(d.getDeviceId());
            synced.setSensorType(d.getSensorType());
            synced.setOriginalTimestamp(d.getTimestamp());
            synced.setSyncedTimestamp(d.getTimestamp());
            synced.setValue(d.getValue());
            synced.setConfidence(1.0);
            synced.setSyncMethod(method);
            synced.setInterpolated(false);
            return synced;
        }).collect(Collectors.toList());
    }
    
    /**
     * 估算原始采样间隔
     */
    private long estimateSamplingInterval(List<SensorData> dataList) {
        if (dataList.size() < 2) return 1000;
        
        long totalInterval = 0;
        int count = 0;
        
        for (int i = 1; i < dataList.size() && i < 10; i++) {
            long interval = ChronoUnit.MILLIS.between(
                    dataList.get(i - 1).getTimestamp(),
                    dataList.get(i).getTimestamp()
            );
            if (interval > 0) {
                totalInterval += interval;
                count++;
            }
        }
        
        return count > 0 ? totalInterval / count : 1000;
    }
    
    // ==================== 兼容旧版方法 ====================
    
    /**
     * 按时间戳排序（兼容旧版）
     */
    public List<SensorData> syncByTimestamp(List<SensorData> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return Collections.emptyList();
        }
        return dataList.stream()
                .filter(d -> d.getTimestamp() != null)
                .sorted(Comparator.comparing(SensorData::getTimestamp))
                .collect(Collectors.toList());
    }
    
    /**
     * 采样率对齐（兼容旧版，使用字符串采样率）
     */
    public List<SensorData> alignSamplingRate(List<SensorData> dataList, String targetRate) {
        long targetIntervalMs = parseRateToMillis(targetRate);
        if (targetIntervalMs <= 0) {
            return dataList;
        }
        int targetRateHz = (int) (1000 / targetIntervalMs);
        List<SyncedData> synced = alignSamplingRate(dataList, targetRateHz, "aggregation");
        return synced.stream().map(s -> {
            SensorData d = new SensorData();
            d.setDeviceId(s.getDeviceId());
            d.setSensorType(s.getSensorType());
            d.setTimestamp(s.getSyncedTimestamp());
            d.setValue(s.getValue());
            return d;
        }).collect(Collectors.toList());
    }
    
    /**
     * 解析采样率字符串为毫秒
     */
    private long parseRateToMillis(String rate) {
        try {
            rate = rate.trim().toLowerCase();
            if (rate.endsWith("ms")) {
                return Long.parseLong(rate.replace("ms", ""));
            } else if (rate.endsWith("s")) {
                return Long.parseLong(rate.replace("s", "")) * 1000;
            } else if (rate.endsWith("m")) {
                return Long.parseLong(rate.replace("m", "")) * 60 * 1000;
            } else if (rate.endsWith("h")) {
                return Long.parseLong(rate.replace("h", "")) * 60 * 60 * 1000;
            } else {
                return Long.parseLong(rate);
            }
        } catch (NumberFormatException e) {
            log.error("解析采样率失败: {}", rate);
            return -1;
        }
    }
}
