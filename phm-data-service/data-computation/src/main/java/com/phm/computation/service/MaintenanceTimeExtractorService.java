package com.phm.computation.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 维修时间抽取服务
 * 
 * 功能：
 * - 从维修记录文本中抽取时间信息
 * - 识别维修开始时间、结束时间、持续时间
 * - 计算维修时长统计
 * 
 * 算法：
 * - 正则表达式匹配时间格式
 * - 关键词定位（开始、结束、耗时、持续等）
 * - 相对时间计算
 */
@Slf4j
@Service
public class MaintenanceTimeExtractorService {

    // 时间格式正则
    private static final Pattern DATE_TIME_PATTERN = Pattern.compile(
        "(\\d{4}[-/年]\\d{1,2}[-/月]\\d{1,2}[日]?\\s*\\d{1,2}[:点时]\\d{1,2}([:分]\\d{1,2})?)"
    );
    
    private static final Pattern DATE_PATTERN = Pattern.compile(
        "(\\d{4}[-/年]\\d{1,2}[-/月]\\d{1,2}[日]?)"
    );
    
    private static final Pattern TIME_PATTERN = Pattern.compile(
        "(\\d{1,2}[:点时]\\d{1,2}([:分]\\d{1,2})?)"
    );
    
    private static final Pattern DURATION_PATTERN = Pattern.compile(
        "(\\d+)\\s*(小时|h|分钟|min|分|天|day)"
    );

    // 时间关键词
    private static final List<String> START_KEYWORDS = Arrays.asList(
        "开始", "启动", "报修", "发现", "故障时间", "开始时间", "报修时间"
    );
    
    private static final List<String> END_KEYWORDS = Arrays.asList(
        "结束", "完成", "修复", "恢复", "解决", "结束时间", "完成时间"
    );
    
    private static final List<String> DURATION_KEYWORDS = Arrays.asList(
        "耗时", "持续", "用时", "维修时长", "停机时间", "共耗时"
    );

    /**
     * 从维修记录中抽取时间信息
     * 
     * @param maintenanceText 维修记录文本
     * @return 时间抽取结果
     */
    public Map<String, Object> extractMaintenanceTime(String maintenanceText) {
        log.info("开始抽取维修时间信息");
        
        Map<String, Object> result = new HashMap<>();
        
        // 1. 抽取所有时间
        List<LocalDateTime> dateTimes = extractDateTimes(maintenanceText);
        result.put("allDateTimes", dateTimes);
        
        // 2. 识别开始时间
        LocalDateTime startTime = identifyStartTime(maintenanceText, dateTimes);
        result.put("startTime", startTime);
        
        // 3. 识别结束时间
        LocalDateTime endTime = identifyEndTime(maintenanceText, dateTimes);
        result.put("endTime", endTime);
        
        // 4. 抽取持续时间
        Integer durationMinutes = extractDuration(maintenanceText);
        result.put("durationMinutes", durationMinutes);
        
        // 5. 计算维修时长
        Integer calculatedDuration = calculateDuration(startTime, endTime, durationMinutes);
        result.put("calculatedDurationMinutes", calculatedDuration);
        
        // 6. 格式化输出
        if (calculatedDuration != null) {
            result.put("durationFormatted", formatDuration(calculatedDuration));
        }
        
        log.info("维修时间抽取完成: startTime={}, endTime={}, duration={}分钟", 
            startTime, endTime, calculatedDuration);
        
        return result;
    }

    /**
     * 批量抽取维修时间
     */
    public List<Map<String, Object>> extractBatch(List<String> maintenanceTexts) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (String text : maintenanceTexts) {
            results.add(extractMaintenanceTime(text));
        }
        return results;
    }

    /**
     * 统计维修时长分布
     */
    public Map<String, Object> analyzeDurationDistribution(List<Integer> durations) {
        if (durations == null || durations.isEmpty()) {
            return Map.of("count", 0, "avgDuration", 0, "maxDuration", 0, "minDuration", 0);
        }

        int max = durations.stream().mapToInt(Integer::intValue).max().orElse(0);
        int min = durations.stream().mapToInt(Integer::intValue).min().orElse(0);
        double avg = durations.stream().mapToInt(Integer::intValue).average().orElse(0);

        // 时长分布
        long shortRepair = durations.stream().filter(d -> d <= 60).count();
        long mediumRepair = durations.stream().filter(d -> d > 60 && d <= 240).count();
        long longRepair = durations.stream().filter(d -> d > 240).count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("count", durations.size());
        stats.put("avgDurationMinutes", Math.round(avg));
        stats.put("avgDurationFormatted", formatDuration((int) avg));
        stats.put("maxDurationMinutes", max);
        stats.put("minDurationMinutes", min);
        stats.put("shortRepairCount", shortRepair);  // <= 1小时
        stats.put("mediumRepairCount", mediumRepair); // 1-4小时
        stats.put("longRepairCount", longRepair);    // > 4小时

        return stats;
    }

    // ==================== 私有方法 ====================

    private List<LocalDateTime> extractDateTimes(String text) {
        List<LocalDateTime> dateTimes = new ArrayList<>();
        
        Matcher matcher = DATE_TIME_PATTERN.matcher(text);
        while (matcher.find()) {
            String dateTimeStr = matcher.group(1);
            LocalDateTime dateTime = parseDateTime(dateTimeStr);
            if (dateTime != null) {
                dateTimes.add(dateTime);
            }
        }
        
        return dateTimes;
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        // 标准化字符串
        String normalized = dateTimeStr
            .replace("年", "-")
            .replace("月", "-")
            .replace("日", " ")
            .replace("点", ":")
            .replace("分", ":")
            .replace("/", "-")
            .trim();
        
        // 尝试多种格式
        List<DateTimeFormatter> formatters = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-M-d H:m:s"),
            DateTimeFormatter.ofPattern("yyyy-M-d H:m")
        );
        
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException e) {
                // 继续尝试下一个格式
            }
        }
        
        return null;
    }

    private LocalDateTime identifyStartTime(String text, List<LocalDateTime> dateTimes) {
        if (dateTimes.isEmpty()) {
            return null;
        }
        
        // 查找关键词位置
        int startKeywordPos = findKeywordPosition(text, START_KEYWORDS);
        
        if (startKeywordPos >= 0) {
            // 找到关键词，找最近的时间
            return findNearestTime(text, startKeywordPos, dateTimes);
        }
        
        // 默认返回第一个时间
        return dateTimes.get(0);
    }

    private LocalDateTime identifyEndTime(String text, List<LocalDateTime> dateTimes) {
        if (dateTimes.size() < 2) {
            return null;
        }
        
        // 查找关键词位置
        int endKeywordPos = findKeywordPosition(text, END_KEYWORDS);
        
        if (endKeywordPos >= 0) {
            return findNearestTime(text, endKeywordPos, dateTimes);
        }
        
        // 默认返回最后一个时间
        return dateTimes.get(dateTimes.size() - 1);
    }

    private int findKeywordPosition(String text, List<String> keywords) {
        String lowerText = text.toLowerCase();
        int minPos = Integer.MAX_VALUE;
        
        for (String keyword : keywords) {
            int pos = lowerText.indexOf(keyword.toLowerCase());
            if (pos >= 0 && pos < minPos) {
                minPos = pos;
            }
        }
        
        return minPos == Integer.MAX_VALUE ? -1 : minPos;
    }

    private LocalDateTime findNearestTime(String text, int keywordPos, List<LocalDateTime> dateTimes) {
        // 简化处理：返回第一个时间
        // 实际应该根据时间字符串在文本中的位置来判断
        return dateTimes.get(0);
    }

    private Integer extractDuration(String text) {
        Matcher matcher = DURATION_PATTERN.matcher(text);
        
        int totalMinutes = 0;
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);
            
            switch (unit) {
                case "小时", "h" -> totalMinutes += value * 60;
                case "分钟", "min", "分" -> totalMinutes += value;
                case "天", "day" -> totalMinutes += value * 24 * 60;
            }
        }
        
        return totalMinutes > 0 ? totalMinutes : null;
    }

    private Integer calculateDuration(LocalDateTime start, LocalDateTime end, Integer extractedDuration) {
        if (extractedDuration != null) {
            return extractedDuration;
        }
        
        if (start != null && end != null) {
            return (int) java.time.Duration.between(start, end).toMinutes();
        }
        
        return null;
    }

    private String formatDuration(int minutes) {
        int hours = minutes / 60;
        int mins = minutes % 60;
        
        if (hours > 0) {
            return hours + "小时" + (mins > 0 ? mins + "分钟" : "");
        }
        return mins + "分钟";
    }
}
