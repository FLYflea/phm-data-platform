package com.phm.collection.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Delta 压缩工具类
 * 
 * 实现原理（参考 Facebook Gorilla 时序压缩论文）：
 * - 采用比特（bit）而非字节（byte）作为存储基本单位
 * - 使用 XOR 编码检测浮点数变化位
 * - 使用 Delta-of-Delta 编码时序差值
 * - 使用 ZigZag 编码将有符号整数映射为无符号整数
 * - 使用 Varint 变长编码，小差值用更少的比特表示
 * 
 * 压缩格式：
 * [4字节:数据长度][8字节:第一个基准值][8字节:第二个值用于计算初始Delta]
 * [变长比特流:后续值的XOR压缩数据]
 */
@Slf4j
@Component
public class DeltaCompressionUtil {

    /**
     * 比特级写入器 - 支持按比特写入数据
     */
    private static class BitWriter {
        private byte[] buffer;
        private int bytePos;      // 当前字节位置
        private int bitPos;       // 当前字节内的比特位置 (0-7)
        
        public BitWriter(int initialCapacity) {
            this.buffer = new byte[initialCapacity];
            this.bytePos = 0;
            this.bitPos = 0;
        }
        
        /**
         * 写入单个比特
         */
        public void writeBit(boolean bit) {
            ensureCapacity(bytePos + 1);
            if (bit) {
                buffer[bytePos] |= (1 << (7 - bitPos));
            }
            bitPos++;
            if (bitPos == 8) {
                bitPos = 0;
                bytePos++;
            }
        }
        
        /**
         * 写入多个比特（从高位到低位）
         * @param value 要写入的值
         * @param numBits 要写入的比特数
         */
        public void writeBits(long value, int numBits) {
            for (int i = numBits - 1; i >= 0; i--) {
                writeBit(((value >> i) & 1) == 1);
            }
        }
        
        /**
         * 写入完整字节
         */
        public void writeByte(byte b) {
            writeBits(b & 0xFF, 8);
        }
        
        /**
         * 写入4字节整数
         */
        public void writeInt(int value) {
            writeBits((value >> 24) & 0xFF, 8);
            writeBits((value >> 16) & 0xFF, 8);
            writeBits((value >> 8) & 0xFF, 8);
            writeBits(value & 0xFF, 8);
        }
        
        /**
         * 写入8字节long
         */
        public void writeLong(long value) {
            for (int i = 56; i >= 0; i -= 8) {
                writeBits((value >> i) & 0xFF, 8);
            }
        }
        
        /**
         * 获取已写入的字节数组
         */
        public byte[] toByteArray() {
            int length = bytePos + (bitPos > 0 ? 1 : 0);
            byte[] result = new byte[length];
            System.arraycopy(buffer, 0, result, 0, length);
            return result;
        }
        
        private void ensureCapacity(int required) {
            if (required > buffer.length) {
                int newSize = Math.max(buffer.length * 2, required);
                byte[] newBuffer = new byte[newSize];
                System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
                buffer = newBuffer;
            }
        }
    }
    
    /**
     * 比特级读取器 - 支持按比特读取数据
     */
    private static class BitReader {
        private final byte[] buffer;
        private int bytePos;      // 当前字节位置
        private int bitPos;       // 当前字节内的比特位置 (0-7)
        
        public BitReader(byte[] data) {
            this.buffer = data;
            this.bytePos = 0;
            this.bitPos = 0;
        }
        
        /**
         * 读取单个比特
         */
        public boolean readBit() {
            if (bytePos >= buffer.length) {
                throw new RuntimeException("读取超出缓冲区范围");
            }
            boolean bit = ((buffer[bytePos] >> (7 - bitPos)) & 1) == 1;
            bitPos++;
            if (bitPos == 8) {
                bitPos = 0;
                bytePos++;
            }
            return bit;
        }
        
        /**
         * 读取多个比特
         * @param numBits 要读取的比特数
         * @return 读取的值
         */
        public long readBits(int numBits) {
            long value = 0;
            for (int i = 0; i < numBits; i++) {
                value = (value << 1) | (readBit() ? 1 : 0);
            }
            return value;
        }
        
        /**
         * 读取4字节整数
         */
        public int readInt() {
            return (int) readBits(32);
        }
        
        /**
         * 读取8字节long
         */
        public long readLong() {
            return readBits(64);
        }
        
        /**
         * 检查是否还有数据可读
         */
        public boolean hasMore() {
            return bytePos < buffer.length;
        }
    }
    
    // ==================== ZigZag 编码 ====================
    
    /**
     * ZigZag 编码：将有符号整数映射为无符号整数
     * 正数 n 映射为 2n，负数 -n 映射为 2n-1
     * 例如：0->0, -1->1, 1->2, -2->3, 2->4, ...
     */
    private static long zigZagEncode(long value) {
        return (value << 1) ^ (value >> 63);
    }
    
    /**
     * ZigZag 解码：将无符号整数还原为有符号整数
     */
    private static long zigZagDecode(long encoded) {
        return (encoded >>> 1) ^ -(encoded & 1);
    }
    
    // ==================== Varint 变长编码 ====================
    
    /**
     * 计算 Varint 编码所需的比特数
     * 使用前缀编码：
     * - 0: 1位前缀(0) + 0位数据 = 值为0
     * - 1-3: 2位前缀(10) + 2位数据 = 4位总计
     * - 4-15: 2位前缀(110) + 4位数据 = 6位总计  
     * - 16-63: 3位前缀(1110) + 6位数据 = 9位总计
     * - 64-255: 4位前缀(11110) + 8位数据 = 12位总计
     * - 256-4095: 5位前缀(111110) + 12位数据 = 17位总计
     * - 更大值: 6位前缀(1111110) + 32位数据 = 38位总计
     * - 超大值: 7位前缀(11111110) + 64位数据 = 71位总计
     */
    private void writeVarint(BitWriter writer, long value) {
        if (value == 0) {
            // 0: 单个0比特
            writer.writeBit(false);
        } else if (value <= 3) {
            // 1-3: 10 + 2位
            writer.writeBit(true);
            writer.writeBit(false);
            writer.writeBits(value, 2);
        } else if (value <= 15) {
            // 4-15: 110 + 4位
            writer.writeBit(true);
            writer.writeBit(true);
            writer.writeBit(false);
            writer.writeBits(value, 4);
        } else if (value <= 63) {
            // 16-63: 1110 + 6位
            writer.writeBits(0b1110, 4);
            writer.writeBits(value, 6);
        } else if (value <= 255) {
            // 64-255: 11110 + 8位
            writer.writeBits(0b11110, 5);
            writer.writeBits(value, 8);
        } else if (value <= 4095) {
            // 256-4095: 111110 + 12位
            writer.writeBits(0b111110, 6);
            writer.writeBits(value, 12);
        } else if (value <= 0xFFFFFFFFL) {
            // 至32位值: 1111110 + 32位
            writer.writeBits(0b1111110, 7);
            writer.writeBits(value, 32);
        } else {
            // 超大值: 11111110 + 64位
            writer.writeBits(0b11111110, 8);
            writer.writeBits(value, 64);
        }
    }
    
    /**
     * 读取 Varint 编码的值
     */
    private long readVarint(BitReader reader) {
        // 读取第一位
        if (!reader.readBit()) {
            return 0; // 0
        }
        // 10: 2位数据
        if (!reader.readBit()) {
            return reader.readBits(2);
        }
        // 110: 4位数据
        if (!reader.readBit()) {
            return reader.readBits(4);
        }
        // 1110: 6位数据
        if (!reader.readBit()) {
            return reader.readBits(6);
        }
        // 11110: 8位数据
        if (!reader.readBit()) {
            return reader.readBits(8);
        }
        // 111110: 12位数据
        if (!reader.readBit()) {
            return reader.readBits(12);
        }
        // 1111110: 32位数据
        if (!reader.readBit()) {
            return reader.readBits(32);
        }
        // 11111110: 64位数据
        return reader.readBits(64);
    }
    
    // ==================== XOR 压缩（Gorilla风格） ====================
    
    /**
     * 使用 XOR 压缩写入浮点数差异
     * 参考 Gorilla 论文的 XOR 压缩策略
     * 
     * @param writer 比特写入器
     * @param xorValue 当前值与前一个值的 XOR 结果
     * @param prevLeadingZeros 前一个值的前导零数
     * @param prevTrailingZeros 前一个值的尾部零数
     * @return int[2] = {新的前导零数, 新的尾部零数}
     */
    private int[] writeXorValue(BitWriter writer, long xorValue, int prevLeadingZeros, int prevTrailingZeros) {
        if (xorValue == 0) {
            // 值相同，写入单个0比特
            writer.writeBit(false);
            return new int[]{prevLeadingZeros, prevTrailingZeros};
        }
        
        // 值不同，写入1表示有变化
        writer.writeBit(true);
        
        int leadingZeros = Long.numberOfLeadingZeros(xorValue);
        int trailingZeros = Long.numberOfTrailingZeros(xorValue);
        int significantBits = 64 - leadingZeros - trailingZeros;
        
        // 检查是否可以重用前一个的前导零和尾部零窗口
        if (leadingZeros >= prevLeadingZeros && trailingZeros >= prevTrailingZeros) {
            // 控制位 0：重用前一个窗口
            writer.writeBit(false);
            int prevSignificantBits = 64 - prevLeadingZeros - prevTrailingZeros;
            // 写入有效位（按前一个窗口大小）
            long meaningfulBits = (xorValue >> prevTrailingZeros) & ((1L << prevSignificantBits) - 1);
            writer.writeBits(meaningfulBits, prevSignificantBits);
            return new int[]{prevLeadingZeros, prevTrailingZeros};
        } else {
            // 控制位 1：新窗口
            writer.writeBit(true);
            // 写入前导零数（6位，最多63）
            writer.writeBits(leadingZeros, 6);
            // 写入有效位数（6位，最多64，这里用63表示64）
            writer.writeBits(significantBits == 64 ? 0 : significantBits, 6);
            // 写入有效位
            long meaningfulBits = (xorValue >> trailingZeros) & ((1L << significantBits) - 1);
            writer.writeBits(meaningfulBits, significantBits);
            return new int[]{leadingZeros, trailingZeros};
        }
    }
    
    /**
     * 使用 XOR 解压读取浮点数
     * 
     * @param reader 比特读取器
     * @param prevValue 前一个值的 long 表示
     * @param prevLeadingZeros 前一个的前导零数
     * @param prevTrailingZeros 前一个的尾部零数
     * @return long[3] = {当前值的long表示, 前导零数, 尾部零数}
     */
    private long[] readXorValue(BitReader reader, long prevValue, int prevLeadingZeros, int prevTrailingZeros) {
        // 读取第一个控制位
        if (!reader.readBit()) {
            // 值相同
            return new long[]{prevValue, prevLeadingZeros, prevTrailingZeros};
        }
        
        // 读取第二个控制位
        if (!reader.readBit()) {
            // 重用前一个窗口
            int significantBits = 64 - prevLeadingZeros - prevTrailingZeros;
            long meaningfulBits = reader.readBits(significantBits);
            long xorValue = meaningfulBits << prevTrailingZeros;
            return new long[]{prevValue ^ xorValue, prevLeadingZeros, prevTrailingZeros};
        } else {
            // 新窗口
            int leadingZeros = (int) reader.readBits(6);
            int significantBits = (int) reader.readBits(6);
            if (significantBits == 0) {
                significantBits = 64; // 0表示64位
            }
            int trailingZeros = 64 - leadingZeros - significantBits;
            long meaningfulBits = reader.readBits(significantBits);
            long xorValue = meaningfulBits << trailingZeros;
            return new long[]{prevValue ^ xorValue, leadingZeros, trailingZeros};
        }
    }

    /**
     * Delta 编码压缩（比特级变长编码版本）
     * 
     * 压缩格式：
     * - 4字节：数据长度
     * - 8字节：第一个值（基准值）
     * - 如果长度>1，8字节：第二个值
     * - 后续：XOR压缩的比特流
     * 
     * @param values 原始 double 数组
     * @return 压缩后的 byte 数组
     */
    public byte[] compress(double[] values) {
        if (values == null || values.length == 0) {
            return new byte[0];
        }

        // 预估初始容量
        int estimatedSize = 12 + values.length * 2; // 保守估计
        BitWriter writer = new BitWriter(estimatedSize);
        
        // 写入数据长度（4字节）
        writer.writeInt(values.length);
        
        // 写入第一个值（完整8字节）
        long firstBits = Double.doubleToLongBits(values[0]);
        writer.writeLong(firstBits);
        
        if (values.length == 1) {
            byte[] compressed = writer.toByteArray();
            log.debug("Delta 压缩完成: 原始 {} 字节, 压缩后 {} 字节", 
                    values.length * 8, compressed.length);
            return compressed;
        }
        
        // 写入第二个值（完整8字节，用于建立初始Delta）
        long secondBits = Double.doubleToLongBits(values[1]);
        writer.writeLong(secondBits);
        
        if (values.length == 2) {
            byte[] compressed = writer.toByteArray();
            log.debug("Delta 压缩完成: 原始 {} 字节, 压缩后 {} 字节", 
                    values.length * 8, compressed.length);
            return compressed;
        }
        
        // 使用 XOR 压缩后续值
        long prevBits = secondBits;
        int leadingZeros = 64; // 初始化为最大值
        int trailingZeros = 64;
        
        for (int i = 2; i < values.length; i++) {
            long currentBits = Double.doubleToLongBits(values[i]);
            long xorValue = currentBits ^ prevBits;
            
            int[] zeros = writeXorValue(writer, xorValue, leadingZeros, trailingZeros);
            leadingZeros = zeros[0];
            trailingZeros = zeros[1];
            
            prevBits = currentBits;
        }
        
        byte[] compressed = writer.toByteArray();
        
        log.debug("Delta 压缩完成: 原始 {} 字节, 压缩后 {} 字节, 压缩率 {:.1f}%", 
                values.length * 8, compressed.length,
                (1.0 - (double) compressed.length / (values.length * 8)) * 100);
        
        return compressed;
    }

    /**
     * Delta 解码解压（比特级变长编码版本）
     * 
     * @param compressed 压缩后的 byte 数组
     * @return 还原后的 double 数组
     */
    public double[] decompress(byte[] compressed) {
        if (compressed == null || compressed.length == 0) {
            return new double[0];
        }

        BitReader reader = new BitReader(compressed);
        
        // 读取数据长度
        int length = reader.readInt();
        double[] values = new double[length];
        
        if (length == 0) {
            return values;
        }
        
        // 读取第一个值
        long firstBits = reader.readLong();
        values[0] = Double.longBitsToDouble(firstBits);
        
        if (length == 1) {
            log.debug("Delta 解压完成: 还原 {} 个数据点", length);
            return values;
        }
        
        // 读取第二个值
        long secondBits = reader.readLong();
        values[1] = Double.longBitsToDouble(secondBits);
        
        if (length == 2) {
            log.debug("Delta 解压完成: 还原 {} 个数据点", length);
            return values;
        }
        
        // XOR 解压后续值
        long prevBits = secondBits;
        int leadingZeros = 64;
        int trailingZeros = 64;
        
        for (int i = 2; i < length; i++) {
            long[] result = readXorValue(reader, prevBits, leadingZeros, trailingZeros);
            long currentBits = result[0];
            leadingZeros = (int) result[1];
            trailingZeros = (int) result[2];
            
            values[i] = Double.longBitsToDouble(currentBits);
            prevBits = currentBits;
        }

        log.debug("Delta 解压完成: 还原 {} 个数据点", length);
        return values;
    }
    
    // ==================== 时间戳专用压缩方法 ====================
    
    /**
     * 时间戳压缩（Delta-of-Delta + ZigZag + Varint 编码）
     * 
     * @param timestamps 时间戳数组（毫秒）
     * @return 压缩后的 byte 数组
     */
    public byte[] compressTimestamps(long[] timestamps) {
        if (timestamps == null || timestamps.length == 0) {
            return new byte[0];
        }
        
        int estimatedSize = 8 + timestamps.length * 2;
        BitWriter writer = new BitWriter(estimatedSize);
        
        // 写入数据长度
        writer.writeInt(timestamps.length);
        
        // 写入第一个时间戳（完整8字节）
        writer.writeLong(timestamps[0]);
        
        if (timestamps.length == 1) {
            return writer.toByteArray();
        }
        
        // 计算并写入第一个Delta
        long prevDelta = timestamps[1] - timestamps[0];
        // 对第一个Delta使用ZigZag + Varint编码
        writeVarint(writer, zigZagEncode(prevDelta));
        
        // 后续使用 Delta-of-Delta 编码
        for (int i = 2; i < timestamps.length; i++) {
            long delta = timestamps[i] - timestamps[i - 1];
            long deltaOfDelta = delta - prevDelta;
            
            // ZigZag编码将有符号转为无符号
            long encoded = zigZagEncode(deltaOfDelta);
            
            // Varint编码写入
            writeVarint(writer, encoded);
            
            prevDelta = delta;
        }
        
        byte[] compressed = writer.toByteArray();
        log.debug("时间戳压缩完成: 原始 {} 字节, 压缩后 {} 字节", 
                timestamps.length * 8, compressed.length);
        
        return compressed;
    }
    
    /**
     * 时间戳解压
     * 
     * @param compressed 压缩后的 byte 数组
     * @return 还原后的时间戳数组
     */
    public long[] decompressTimestamps(byte[] compressed) {
        if (compressed == null || compressed.length == 0) {
            return new long[0];
        }
        
        BitReader reader = new BitReader(compressed);
        
        // 读取数据长度
        int length = reader.readInt();
        long[] timestamps = new long[length];
        
        if (length == 0) {
            return timestamps;
        }
        
        // 读取第一个时间戳
        timestamps[0] = reader.readLong();
        
        if (length == 1) {
            return timestamps;
        }
        
        // 读取第一个Delta
        long prevDelta = zigZagDecode(readVarint(reader));
        timestamps[1] = timestamps[0] + prevDelta;
        
        // 解码 Delta-of-Delta
        for (int i = 2; i < length; i++) {
            long deltaOfDelta = zigZagDecode(readVarint(reader));
            long delta = prevDelta + deltaOfDelta;
            timestamps[i] = timestamps[i - 1] + delta;
            prevDelta = delta;
        }
        
        log.debug("时间戳解压完成: 还原 {} 个时间戳", length);
        return timestamps;
    }
    
    // ==================== 组合压缩方法（时间戳 + 数值） ====================
    
    /**
     * 时序数据完整压缩（时间戳 + 数值）
     * 
     * @param timestamps 时间戳数组
     * @param values 数值数组
     * @return 压缩后的 byte 数组
     */
    public byte[] compressTimeSeries(long[] timestamps, double[] values) {
        if (timestamps == null || values == null || 
            timestamps.length == 0 || timestamps.length != values.length) {
            return new byte[0];
        }
        
        byte[] compressedTimestamps = compressTimestamps(timestamps);
        byte[] compressedValues = compress(values);
        
        // 组合格式：[4字节:时间戳压缩长度][时间戳压缩数据][数值压缩数据]
        int totalSize = 4 + compressedTimestamps.length + compressedValues.length;
        BitWriter writer = new BitWriter(totalSize);
        
        writer.writeInt(compressedTimestamps.length);
        for (byte b : compressedTimestamps) {
            writer.writeByte(b);
        }
        for (byte b : compressedValues) {
            writer.writeByte(b);
        }
        
        byte[] result = writer.toByteArray();
        log.debug("时序数据压缩完成: 原始 {} 字节, 压缩后 {} 字节",
                (timestamps.length * 8 + values.length * 8), result.length);
        
        return result;
    }
    
    /**
     * 时序数据完整解压
     * 
     * @param compressed 压缩后的 byte 数组
     * @return Object[2] = {long[] timestamps, double[] values}
     */
    public Object[] decompressTimeSeries(byte[] compressed) {
        if (compressed == null || compressed.length == 0) {
            return new Object[]{new long[0], new double[0]};
        }
        
        BitReader reader = new BitReader(compressed);
        
        // 读取时间戳压缩数据长度
        int timestampLen = reader.readInt();
        
        // 读取时间戳压缩数据
        byte[] compressedTimestamps = new byte[timestampLen];
        for (int i = 0; i < timestampLen; i++) {
            compressedTimestamps[i] = (byte) reader.readBits(8);
        }
        
        // 读取剩余的数值压缩数据
        int remainingBytes = compressed.length - 4 - timestampLen;
        byte[] compressedValues = new byte[remainingBytes];
        for (int i = 0; i < remainingBytes; i++) {
            compressedValues[i] = (byte) reader.readBits(8);
        }
        
        long[] timestamps = decompressTimestamps(compressedTimestamps);
        double[] values = decompress(compressedValues);
        
        return new Object[]{timestamps, values};
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
    
    /**
     * 计算时序数据的压缩比
     * 
     * @param timestamps 原始时间戳数组
     * @param values 原始数值数组
     * @param compressed 压缩后的 byte 数组
     * @return 压缩比（百分比）
     */
    public double calculateCompressionRatio(long[] timestamps, double[] values, byte[] compressed) {
        if (timestamps == null || values == null) {
            return 0.0;
        }
        int originalSize = timestamps.length * 8 + values.length * 8;
        int compressedSize = compressed != null ? compressed.length : 0;
        return calculateCompressionRatio(originalSize, compressedSize);
    }
}
