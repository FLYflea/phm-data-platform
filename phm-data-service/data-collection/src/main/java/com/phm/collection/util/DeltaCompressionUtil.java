package com.phm.collection.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Delta 压缩工具类
 * 
 * 实现原理：
 * - 存储第一个原始值（基准值）
 * - 后续存储相邻值的差值（Delta值）
 * 
 * TODO: 待优化
 * - 当前使用8字节存储每个Delta值，实际可采用变长编码（如Varint、ZigZag）进一步压缩
 * - 比特级变长编码待优化
 */
@Slf4j
@Component
public class DeltaCompressionUtil {

    /**
     * Delta 编码压缩
     * 
     * 格式：[4字节:数据长度][8字节:基准值][8字节×(n-1):Delta值列表]
     * 
     * @param values 原始 double 数组
     * @return 压缩后的 byte 数组
     */
    public byte[] compress(double[] values) {
        if (values == null || values.length == 0) {
            return new byte[0];
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {

            // 写入数据长度（4字节 int）
            dos.writeInt(values.length);

            if (values.length > 0) {
                // 写入第一个值作为基准（8字节 double）
                dos.writeDouble(values[0]);

                // 写入 Delta 值（当前值 - 前一个值）
                // TODO: 比特级变长编码待优化 - 当前固定8字节存储，实际差值可能很小
                for (int i = 1; i < values.length; i++) {
                    double delta = values[i] - values[i - 1];
                    dos.writeDouble(delta);
                }
            }

            dos.flush();
            byte[] compressed = baos.toByteArray();
            
            log.debug("Delta 压缩完成: 原始 {} 字节, 压缩后 {} 字节", 
                    values.length * 8, compressed.length);
            
            return compressed;

        } catch (IOException e) {
            log.error("Delta 压缩失败: {}", e.getMessage(), e);
            throw new RuntimeException("Delta 压缩失败", e);
        }
    }

    /**
     * Delta 解码解压
     * 
     * @param compressed 压缩后的 byte 数组
     * @return 还原后的 double 数组
     */
    public double[] decompress(byte[] compressed) {
        if (compressed == null || compressed.length == 0) {
            return new double[0];
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
             DataInputStream dis = new DataInputStream(bais)) {

            // 读取数据长度
            int length = dis.readInt();
            double[] values = new double[length];

            if (length > 0) {
                // 读取基准值
                values[0] = dis.readDouble();

                // 还原后续值：当前值 = 前一个值 + Delta
                for (int i = 1; i < length; i++) {
                    double delta = dis.readDouble();
                    values[i] = values[i - 1] + delta;
                }
            }

            log.debug("Delta 解压完成: 还原 {} 个数据点", length);
            return values;

        } catch (IOException e) {
            log.error("Delta 解压失败: {}", e.getMessage(), e);
            throw new RuntimeException("Delta 解压失败", e);
        }
    }

    /**
     * 计算压缩比
     * 
     * @param originalSize 原始数据大小（字节）
     * @param compressedSize 压缩后大小（字节）
     * @return 压缩比（百分比，如 50.0 表示压缩了50%）
     */
    public double calculateCompressionRatio(int originalSize, int compressedSize) {
        if (originalSize <= 0) {
            return 0.0;
        }
        double ratio = (1.0 - (double) compressedSize / originalSize) * 100;
        log.info("压缩比: 原始 {} 字节 -> 压缩后 {} 字节, 压缩率 {:.2f}%", 
                originalSize, compressedSize, ratio);
        return ratio;
    }

    /**
     * 计算 double 数组的压缩比（便捷方法）
     * 
     * @param values 原始 double 数组
     * @param compressed 压缩后的 byte 数组
     * @return 压缩比（百分比）
     */
    public double calculateCompressionRatio(double[] values, byte[] compressed) {
        if (values == null) {
            return 0.0;
        }
        int originalSize = values.length * 8; // double 占 8 字节
        int compressedSize = compressed != null ? compressed.length : 0;
        return calculateCompressionRatio(originalSize, compressedSize);
    }
}
