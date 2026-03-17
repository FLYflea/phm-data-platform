package com.phm.storage.service.timeseries;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 存储块管理器 - 列分解存储模型核心组件
 * 
 * 设计思想（符合任务书要求）：
 * 1. 为每个传感器分配独立存储区（列分解存储）
 * 2. 以块为单位管理存储空间（块大小可配置）
 * 3. 支持存储区动态扩展
 * 4. 多区缓冲池管理器综合管理缓冲区
 * 5. 提供数据同步服务
 * 
 * @author PHM Platform
 */
@Slf4j
@Component
public class StorageBlockManager {

    /**
     * 存储区映射表：deviceId:sensorType -> StorageZone
     */
    private final Map<String, StorageZone> storageZones = new ConcurrentHashMap<>();

    /**
     * 默认块大小（毫秒）- 1 小时
     */
    private static final long DEFAULT_BLOCK_SIZE_MS = 3600_000L;

    /**
     * 多区缓冲池管理器
     */
    private final MultiZoneBufferPool bufferPool = new MultiZoneBufferPool();

    /**
     * 获取或创建存储区
     * 
     * @param deviceId 设备 ID
     * @param sensorType 传感器类型
     * @return 存储区对象
     */
    public StorageZone getOrCreateZone(String deviceId, String sensorType) {
        String key = buildZoneKey(deviceId, sensorType);
        
        return storageZones.computeIfAbsent(key, k -> {
            log.info("创建新的存储区：deviceId={}, sensorType={}", deviceId, sensorType);
            StorageZone zone = new StorageZone(deviceId, sensorType, DEFAULT_BLOCK_SIZE_MS);
            bufferPool.registerZone(zone); // 注册到缓冲池
            return zone;
        });
    }

    /**
     * 构建存储区键
     */
    private String buildZoneKey(String deviceId, String sensorType) {
        return deviceId + ":" + sensorType;
    }

    /**
     * 获取所有存储区统计信息
     */
    public Map<String, StorageZone> getAllStorageZones() {
        return storageZones;
    }

    /**
     * 清理存储区（用于测试）
     */
    public void clearZone(String deviceId, String sensorType) {
        String key = buildZoneKey(deviceId, sensorType);
        StorageZone zone = storageZones.remove(key);
        if (zone != null) {
            bufferPool.unregisterZone(zone);
        }
        log.info("已清理存储区：{}", key);
    }

    /**
     * 获取缓冲池管理器
     */
    public MultiZoneBufferPool getBufferPool() {
        return bufferPool;
    }

    /**
     * 内部类：多区缓冲池管理器
     * 
     * 职责：
     * 1. 综合管理所有存储区的缓冲区
     * 2. 提供基本的数据同步服务
     * 3. 支持数据驱动模型的构造与使用
     */
    public static class MultiZoneBufferPool {
        
        /**
         * 已注册的存储区列表
         */
        private final List<StorageZone> registeredZones = new ArrayList<>();
        
        /**
         * 锁机制 - 保证线程安全
         */
        private final ReentrantLock lock = new ReentrantLock();
        
        /**
         * 注册存储区到缓冲池
         */
        public void registerZone(StorageZone zone) {
            lock.lock();
            try {
                registeredZones.add(zone);
                log.debug("存储区已注册到缓冲池：{}:{}", zone.getDeviceId(), zone.getSensorType());
            } finally {
                lock.unlock();
            }
        }
        
        /**
         * 从缓冲池注销存储区
         */
        public void unregisterZone(StorageZone zone) {
            lock.lock();
            try {
                registeredZones.remove(zone);
                log.debug("存储区已从缓冲池注销：{}:{}", zone.getDeviceId(), zone.getSensorType());
            } finally {
                lock.unlock();
            }
        }
        
        /**
         * 数据同步 - 将所有缓冲区数据刷新到持久化存储
         */
        public void syncAll() {
            lock.lock();
            try {
                for (StorageZone zone : registeredZones) {
                    zone.flushToPersistent();
                }
                log.info("缓冲池数据同步完成，共 {} 个存储区", registeredZones.size());
            } finally {
                lock.unlock();
            }
        }
        
        /**
         * 获取所有注册的存储区
         */
        public List<StorageZone> getAllZones() {
            return new ArrayList<>(registeredZones);
        }
    }

    /**
     * 内部类：存储区 - 列分解存储模型的基本单元
     * 
     * 职责：
     * 1. 元数据存储与单元时间序列存储
     * 2. 以块为单位管理，支持动态扩展
     */
    public static class StorageZone {
        
        private final String deviceId;
        private final String sensorType;
        private final long blockSizeMs;
        
        /**
         * 存储块列表
         */
        private final List<StorageBlock> blocks = new ArrayList<>();
        
        /**
         * 当前写入块
         */
        private StorageBlock currentBlock;
        
        /**
         * 锁机制 - 保证线程安全
         */
        private final ReentrantLock lock = new ReentrantLock();
        
        /**
         * 元数据存储
         */
        private final Map<String, Object> metadata = new ConcurrentHashMap<>();

        /**
         * 构造函数
         */
        public StorageZone(String deviceId, String sensorType, long blockSizeMs) {
            this.deviceId = deviceId;
            this.sensorType = sensorType;
            this.blockSizeMs = blockSizeMs;
            this.currentBlock = new StorageBlock(0, blockSizeMs);
            this.blocks.add(currentBlock);
        }

        /**
         * 写入数据点
         */
        public void write(long timestamp, double value) {
            lock.lock();
            try {
                // 检查是否需要创建新块
                if (!currentBlock.canWrite(timestamp)) {
                    currentBlock = new StorageBlock(timestamp, blockSizeMs);
                    blocks.add(currentBlock);
                    log.debug("创建新存储块：timestamp={}, blockCount={}", timestamp, blocks.size());
                }
                
                // 写入当前块
                currentBlock.write(timestamp, value);
            } finally {
                lock.unlock();
            }
        }

        /**
         * 批量写入数据
         */
        public void batchWrite(long[] timestamps, double[] values) {
            if (timestamps.length != values.length) {
                throw new IllegalArgumentException("时间戳和数值数组长度不匹配");
            }
            
            lock.lock();
            try {
                for (int i = 0; i < timestamps.length; i++) {
                    write(timestamps[i], values[i]);
                }
            } finally {
                lock.unlock();
            }
        }

        /**
         * 查询指定时间范围的数据
         */
        public List<double[]> query(long start, long end) {
            lock.lock();
            try {
                List<double[]> results = new ArrayList<>();
                
                for (StorageBlock block : blocks) {
                    if (block.overlaps(start, end)) {
                        results.addAll(block.query(start, end));
                    }
                }
                
                return results;
            } finally {
                lock.unlock();
            }
        }

        /**
         * 刷新到持久化存储（预留接口）
         */
        public void flushToPersistent() {
            // TODO: 实现持久化逻辑，将内存中的数据写入 PostgreSQL/TimescaleDB
            log.debug("刷新存储区到持久化存储：{}:{}, 块数：{}",
                     deviceId, sensorType, blocks.size());
        }

        // Getter 方法
        public String getDeviceId() { return deviceId; }
        public String getSensorType() { return sensorType; }
        public long getBlockSizeMs() { return blockSizeMs; }
        public int getBlockCount() { return blocks.size(); }
        public Map<String, Object> getMetadata() { return metadata; }
    }

    /**
     * 内部类：存储块 - 基本的存储管理单元
     */
    public static class StorageBlock {
        
        private final long startTime;
        private final long blockSizeMs;
        private final List<long[]> dataPoints = new ArrayList<>();
        
        public StorageBlock(long startTime, long blockSizeMs) {
            this.startTime = startTime;
            this.blockSizeMs = blockSizeMs;
        }
        
        /**
         * 检查是否可以写入该时间戳
         */
        public boolean canWrite(long timestamp) {
            return timestamp >= startTime && timestamp < startTime + blockSizeMs;
        }
        
        /**
         * 写入数据点
         */
        public void write(long timestamp, double value) {
            // 使用位压缩存储：高 32 位时间戳偏移，低 32 位数值
            long compressed = ((timestamp - startTime) << 32) | Double.doubleToRawLongBits(value);
            dataPoints.add(new long[]{compressed});
        }
        
        /**
         * 查询时间范围内的数据
         */
        public List<double[]> query(long start, long end) {
            List<double[]> results = new ArrayList<>();
            
            for (long[] point : dataPoints) {
                long offset = point[0] >>> 32;
                long timestamp = startTime + offset;
                
                if (timestamp >= start && timestamp <= end) {
                    double value = Double.longBitsToDouble(point[0] & 0xFFFFFFFFL);
                    results.add(new double[]{timestamp, value});
                }
            }
            
            return results;
        }
        
        /**
         * 检查块是否与查询范围重叠
         */
        public boolean overlaps(long start, long end) {
            long blockEnd = startTime + blockSizeMs;
            return !(end < startTime || start > blockEnd);
        }
    }
}
