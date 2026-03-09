package com.phm.computation.controller;

import com.phm.computation.service.MaintenanceTimeExtractorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 维修时间抽取控制器
 * 
 * 职责：
 * - 从维修记录文本中抽取时间信息
 * - 计算维修时长
 * - 统计维修时间分布
 */
@Slf4j
@RestController
@RequestMapping("/computation/maintenance")
@RequiredArgsConstructor
public class MaintenanceTimeController {

    private final MaintenanceTimeExtractorService maintenanceTimeExtractorService;

    /**
     * 抽取单条维修记录的时间信息
     * 
     * @param request 包含 maintenanceText
     * @return 时间抽取结果
     */
    @PostMapping("/extract-time")
    public ResponseEntity<Map<String, Object>> extractMaintenanceTime(@RequestBody Map<String, String> request) {
        String maintenanceText = request.get("maintenanceText");
        
        log.info("接收维修时间抽取请求，文本长度: {}", 
            maintenanceText != null ? maintenanceText.length() : 0);

        if (maintenanceText == null || maintenanceText.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(buildErrorResponse("维修记录文本不能为空"));
        }

        try {
            Map<String, Object> result = maintenanceTimeExtractorService.extractMaintenanceTime(maintenanceText);
            result.put("status", "success");
            
            log.info("维修时间抽取完成: startTime={}, duration={}分钟", 
                result.get("startTime"), result.get("calculatedDurationMinutes"));
            
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("维修时间抽取失败: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(buildErrorResponse("抽取失败: " + e.getMessage()));
        }
    }

    /**
     * 批量抽取维修时间
     */
    @PostMapping("/extract-time/batch")
    public ResponseEntity<Map<String, Object>> extractBatch(@RequestBody Map<String, List<String>> request) {
        List<String> maintenanceTexts = request.get("maintenanceTexts");
        
        log.info("接收批量维修时间抽取请求，数量: {}", 
            maintenanceTexts != null ? maintenanceTexts.size() : 0);

        if (maintenanceTexts == null || maintenanceTexts.isEmpty()) {
            return ResponseEntity.badRequest().body(buildErrorResponse("维修记录列表不能为空"));
        }

        try {
            List<Map<String, Object>> results = maintenanceTimeExtractorService.extractBatch(maintenanceTexts);
            
            // 提取所有时长进行统计
            List<Integer> durations = results.stream()
                .map(r -> (Integer) r.get("calculatedDurationMinutes"))
                .filter(d -> d != null)
                .toList();
            
            Map<String, Object> stats = maintenanceTimeExtractorService.analyzeDurationDistribution(durations);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("results", results);
            response.put("statistics", stats);
            
            log.info("批量维修时间抽取完成，共 {} 条", results.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("批量维修时间抽取失败: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(buildErrorResponse("抽取失败: " + e.getMessage()));
        }
    }

    /**
     * 统计维修时长分布
     */
    @PostMapping("/duration-statistics")
    public ResponseEntity<Map<String, Object>> analyzeDurationStatistics(@RequestBody Map<String, List<Integer>> request) {
        List<Integer> durations = request.get("durations");
        
        log.info("接收维修时长统计请求，数据量: {}", 
            durations != null ? durations.size() : 0);

        if (durations == null || durations.isEmpty()) {
            return ResponseEntity.badRequest().body(buildErrorResponse("时长数据不能为空"));
        }

        try {
            Map<String, Object> stats = maintenanceTimeExtractorService.analyzeDurationDistribution(durations);
            stats.put("status", "success");
            
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("维修时长统计失败: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(buildErrorResponse("统计失败: " + e.getMessage()));
        }
    }

    // ==================== 私有方法 ====================

    private Map<String, Object> buildErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        return response;
    }
}
