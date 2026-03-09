package com.phm.storage.controller;

import com.phm.storage.entity.fmeca.FailureMode;
import com.phm.storage.service.FailureModeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FMECA 故障模式控制器
 * 
 * 职责：
 * - 故障模式 CRUD 接口
 * - RPN 统计查询
 * - 高风险模式识别
 */
@Slf4j
@RestController
@RequestMapping("/storage/fmeca")
@RequiredArgsConstructor
public class FmecaController {

    private final FailureModeService failureModeService;

    /**
     * 保存故障模式
     * 
     * @param failureMode 故障模式数据
     * @return 保存结果（含自动计算的RPN）
     */
    @PostMapping("/failure-mode")
    public Map<String, Object> saveFailureMode(@RequestBody FailureMode failureMode) {
        log.info("保存故障模式: name={}, equipmentId={}", 
            failureMode.getName(), failureMode.getEquipmentId());

        FailureMode saved = failureModeService.saveFailureMode(failureMode);

        Map<String, Object> response = new HashMap<>();
        response.put("failureModeId", saved.getFailureModeId());
        response.put("name", saved.getName());
        response.put("rpn", saved.getRpn());
        response.put("status", "success");

        log.info("故障模式保存成功: id={}, RPN={}", saved.getFailureModeId(), saved.getRpn());
        return response;
    }

    /**
     * 批量保存故障模式
     */
    @PostMapping("/failure-mode/batch")
    public Map<String, Object> saveBatch(@RequestBody List<FailureMode> failureModes) {
        log.info("批量保存故障模式，数量: {}", failureModes.size());

        List<FailureMode> saved = failureModeService.saveBatch(failureModes);

        Map<String, Object> response = new HashMap<>();
        response.put("savedCount", saved.size());
        response.put("status", "success");

        return response;
    }

    /**
     * 根据ID获取故障模式
     */
    @GetMapping("/failure-mode/{failureModeId}")
    public Map<String, Object> getFailureMode(@PathVariable String failureModeId) {
        log.info("查询故障模式: id={}", failureModeId);

        var optional = failureModeService.getById(failureModeId);

        Map<String, Object> response = new HashMap<>();
        if (optional.isPresent()) {
            response.put("data", optional.get());
            response.put("status", "success");
        } else {
            response.put("status", "not_found");
            response.put("message", "故障模式不存在: " + failureModeId);
        }

        return response;
    }

    /**
     * 根据设备ID查询故障模式
     */
    @GetMapping("/failure-mode/equipment/{equipmentId}")
    public Map<String, Object> getByEquipment(@PathVariable String equipmentId) {
        log.info("查询设备故障模式: equipmentId={}", equipmentId);

        List<FailureMode> modes = failureModeService.getByEquipmentId(equipmentId);

        Map<String, Object> response = new HashMap<>();
        response.put("data", modes);
        response.put("count", modes.size());
        response.put("status", "success");

        return response;
    }

    /**
     * 获取高风险故障模式（按RPN排序）
     */
    @GetMapping("/failure-mode/high-risk")
    public Map<String, Object> getHighRiskModes(@RequestParam(defaultValue = "10") int limit) {
        log.info("查询高风险故障模式，限制: {}", limit);

        List<FailureMode> modes = failureModeService.getHighRiskModes(limit);

        Map<String, Object> response = new HashMap<>();
        response.put("data", modes);
        response.put("count", modes.size());
        response.put("status", "success");

        return response;
    }

    /**
     * 获取RPN统计信息
     */
    @GetMapping("/statistics/rpn")
    public Map<String, Object> getRPNStatistics() {
        log.info("查询RPN统计");

        Map<String, Object> stats = failureModeService.getRPNStatistics();
        stats.put("status", "success");

        return stats;
    }

    /**
     * 搜索故障模式
     */
    @GetMapping("/failure-mode/search")
    public Map<String, Object> searchFailureModes(@RequestParam String keyword) {
        log.info("搜索故障模式: keyword={}", keyword);

        List<FailureMode> modes = failureModeService.search(keyword);

        Map<String, Object> response = new HashMap<>();
        response.put("data", modes);
        response.put("count", modes.size());
        response.put("keyword", keyword);
        response.put("status", "success");

        return response;
    }

    /**
     * 删除故障模式
     */
    @DeleteMapping("/failure-mode/{failureModeId}")
    public Map<String, Object> deleteFailureMode(@PathVariable String failureModeId) {
        log.info("删除故障模式: id={}", failureModeId);

        boolean deleted = failureModeService.deleteFailureMode(failureModeId);

        Map<String, Object> response = new HashMap<>();
        if (deleted) {
            response.put("status", "success");
            response.put("message", "删除成功");
        } else {
            response.put("status", "not_found");
            response.put("message", "故障模式不存在");
        }

        return response;
    }
}
