package com.phm.computation.service;

import com.phm.computation.entity.SensorData;
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
 * - 采样率对齐（上采样/下采样）
 * 
 * TODO: 不确定性隶属度算法待实现，当前为简化版
 * - 当前使用确定性算法（排序、平均、线性插值）
 * - 实际应引入不确定性理论，为每个数据点计算时间隶属度
 * - 考虑使用模糊逻辑或证据理论处理时间不确定性
 */
@Slf4j
@Service
public class TimeSyncService {

    /**
     * 按时间戳排序，处理乱序到达的数据
     * 
     * TODO: 不确定性隶属度算法待实现，当前为简化版
     * - 当前仅做简单排序
     * - 实际应考虑数据到达延迟的时间窗口，计算时间可信度
     * 
     * @param dataList 传感器数据列表（可能乱序）
     * @return 按时间戳排序后的数据列表
     */
    public List<SensorData> syncByTimestamp(List<SensorData> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            log.warn("数据列表为空，无需同步");
            return Collections.emptyList();
        }

        log.info("开始时间戳同步，数据条数: {}", dataList.size());

        // 过滤掉时间戳为 null 的数据
        List<SensorData> validData = dataList.stream()
                .filter(d -> d.getTimestamp() != null)
                .collect(Collectors.toList());

        if (validData.size() < dataList.size()) {
            log.warn("过滤掉 {} 条时间戳为空的数据", dataList.size() - validData.size());
        }

        // 按时间戳排序
        List<SensorData> sortedData = validData.stream()
                .sorted(Comparator.comparing(SensorData::getTimestamp))
                .collect(Collectors.toList());

        // 检测乱序情况
        int outOfOrderCount = detectOutOfOrder(dataList, sortedData);
        if (outOfOrderCount > 0) {
            log.info("检测到 {} 条乱序数据，已重新排序", outOfOrderCount);
        }

        log.info("时间戳同步完成，输出 {} 条数据", sortedData.size());
        return sortedData;
    }

    /**
     * 采样率对齐
     * 
     * TODO: 不确定性隶属度算法待实现，当前为简化版
     * - 当前使用简单平均和线性插值
     * - 实际应在聚合/插值时计算数据的不确定性传播
     * 
     * @param dataList 传感器数据列表（已按时间排序）
     * @param targetRate 目标采样率（如 "1s"=1秒, "100ms"=100毫秒, "1m"=1分钟）
     * @return 采样率对齐后的数据列表
     */
    public List<SensorData> alignSamplingRate(List<SensorData> dataList, String targetRate) {
        if (dataList == null || dataList.isEmpty()) {
            log.warn("数据列表为空，无需对齐");
            return Collections.emptyList();
        }

        if (targetRate == null || targetRate.isEmpty()) {
            log.warn("目标采样率未指定，返回原始数据");
            return dataList;
        }

        // 解析目标采样率（转换为毫秒）
        long targetIntervalMs = parseRateToMillis(targetRate);
        if (targetIntervalMs <= 0) {
            log.error("无法解析采样率: {}", targetRate);
            return dataList;
        }

        log.info("开始采样率对齐，数据条数: {}, 目标采样率: {} ({}ms)", 
                dataList.size(), targetRate, targetIntervalMs);

        // 先按时间排序
        List<SensorData> sortedData = syncByTimestamp(dataList);

        if (sortedData.size() < 2) {
            log.warn("数据点不足，无法对齐采样率");
            return sortedData;
        }

        // 估算原始采样率
        long originalIntervalMs = estimateSamplingInterval(sortedData);
        log.info("原始采样率估算: {}ms", originalIntervalMs);

        List<SensorData> alignedData;
        if (originalIntervalMs > targetIntervalMs) {
            // 原始采样率低（间隔大）→ 需要上采样（插值）
            log.info("执行上采样：{}ms → {}ms", originalIntervalMs, targetIntervalMs);
            alignedData = upsample(sortedData, targetIntervalMs);
        } else if (originalIntervalMs < targetIntervalMs) {
            // 原始采样率高（间隔小）→ 需要下采样（聚合）
            log.info("执行下采样：{}ms → {}ms", originalIntervalMs, targetIntervalMs);
            alignedData = downsample(sortedData, targetIntervalMs);
        } else {
            // 采样率相同，无需处理
            log.info("采样率相同，无需对齐");
            alignedData = sortedData;
        }

        log.info("采样率对齐完成，输出 {} 条数据", alignedData.size());
        return alignedData;
    }

    /**
     * 下采样：高采样率 → 局部平均聚合
     * 
     * @param dataList 原始数据（已排序）
     * @param targetIntervalMs 目标采样间隔（毫秒）
     * @return 下采样后的数据
     */
    private List<SensorData> downsample(List<SensorData> dataList, long targetIntervalMs) {
        List<SensorData> result = new ArrayList<>();
        
        if (dataList.isEmpty()) return result;

        LocalDateTime startTime = dataList.get(0).getTimestamp();
        LocalDateTime currentWindowStart = startTime;
        LocalDateTime currentWindowEnd = startTime.plus(targetIntervalMs, ChronoUnit.MILLIS);
        
        List<SensorData> windowData = new ArrayList<>();
        String deviceId = dataList.get(0).getDeviceId();
        String sensorType = dataList.get(0).getSensorType();

        for (SensorData data : dataList) {
            LocalDateTime timestamp = data.getTimestamp();
            
            if (timestamp.isBefore(currentWindowEnd)) {
                // 在当前窗口内
                windowData.add(data);
            } else {
                // 窗口结束，计算平均值
                if (!windowData.isEmpty()) {
                    SensorData aggregated = aggregateWindow(windowData, currentWindowStart, deviceId, sensorType);
                    result.add(aggregated);
                }
                
                // 移动窗口
                while (!timestamp.isBefore(currentWindowEnd)) {
                    currentWindowStart = currentWindowEnd;
                    currentWindowEnd = currentWindowStart.plus(targetIntervalMs, ChronoUnit.MILLIS);
                }
                
                windowData.clear();
                windowData.add(data);
            }
        }
        
        // 处理最后一个窗口
        if (!windowData.isEmpty()) {
            SensorData aggregated = aggregateWindow(windowData, currentWindowStart, deviceId, sensorType);
            result.add(aggregated);
        }

        return result;
    }

    /**
     * 上采样：低采样率 → 线性插值填充
     * 
     * @param dataList 原始数据（已排序）
     * @param targetIntervalMs 目标采样间隔（毫秒）
     * @return 上采样后的数据
     */
    private List<SensorData> upsample(List<SensorData> dataList, long targetIntervalMs) {
        List<SensorData> result = new ArrayList<>();
        
        if (dataList.size() < 2) return dataList;

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
            result.add(current);
            
            // 线性插值填充
            for (int j = 1; j < numPoints; j++) {
                double ratio = (double) j / numPoints;
                double interpolatedValue = currentValue + (nextValue - currentValue) * ratio;
                LocalDateTime interpolatedTime = currentTime.plus(j * targetIntervalMs, ChronoUnit.MILLIS);
                
                SensorData interpolated = new SensorData();
                interpolated.setDeviceId(deviceId);
                interpolated.setSensorType(sensorType);
                interpolated.setValue(interpolatedValue);
                interpolated.setTimestamp(interpolatedTime);
                
                result.add(interpolated);
            }
        }
        
        // 添加最后一个点
        result.add(dataList.get(dataList.size() - 1));

        return result;
    }

    /**
     * 聚合窗口数据（取平均值）
     * 
     * TODO: 不确定性隶属度算法待实现，当前为简化版
     * - 当前使用简单平均
     * - 实际应计算加权平均，权重基于时间隶属度
     */
    private SensorData aggregateWindow(List<SensorData> windowData, LocalDateTime windowTime, 
                                       String deviceId, String sensorType) {
        double avgValue = windowData.stream()
                .mapToDouble(SensorData::getValue)
                .average()
                .orElse(0.0);
        
        SensorData aggregated = new SensorData();
        aggregated.setDeviceId(deviceId);
        aggregated.setSensorType(sensorType);
        aggregated.setValue(avgValue);
        aggregated.setTimestamp(windowTime);
        
        return aggregated;
    }

    /**
     * 估算原始采样间隔
     */
    private long estimateSamplingInterval(List<SensorData> dataList) {
        if (dataList.size() < 2) return 0;
        
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
        
        return count > 0 ? totalInterval / count : 1000; // 默认1秒
    }

    /**
     * 解析采样率字符串为毫秒
     * 
     * 支持格式：100ms, 1s, 5s, 1m, 1h
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
                // 默认按毫秒解析
                return Long.parseLong(rate);
            }
        } catch (NumberFormatException e) {
            log.error("解析采样率失败: {}", rate);
            return -1;
        }
    }

    /**
     * 检测乱序数据数量
     */
    private int detectOutOfOrder(List<SensorData> original, List<SensorData> sorted) {
        int count = 0;
        for (int i = 0; i < original.size() && i < sorted.size(); i++) {
            if (original.get(i) != sorted.get(i)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 批量时间同步（多设备）
     * 
     * @param dataMap 按设备分组的数据 Map<deviceId, List<SensorData>>
     * @return 同步后的数据 Map
     */
    public Map<String, List<SensorData>> syncMultipleDevices(Map<String, List<SensorData>> dataMap) {
        Map<String, List<SensorData>> result = new HashMap<>();
        
        for (Map.Entry<String, List<SensorData>> entry : dataMap.entrySet()) {
            String deviceId = entry.getKey();
            List<SensorData> synced = syncByTimestamp(entry.getValue());
            result.put(deviceId, synced);
        }
        
        return result;
    }
}
