package com.phm.storage.service;

import com.phm.storage.entity.fmeca.FailureMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 故障模式存储服务（FMECA）
 * 
 * 功能：
 * - 故障模式 CRUD
 * - RPN 计算与排序
 * - 按设备/组件查询
 * - 高风险模式识别
 * 
 * TODO: P2 待接入真实数据库（PostgreSQL/MongoDB）
 */
@Slf4j
@Service
public class FailureModeService {

    /**
     * 内存存储（模拟数据库）
     */
    private final Map<String, FailureMode> failureModeStore = new ConcurrentHashMap<>();

    /**
     * 保存故障模式
     * 
     * @param failureMode 故障模式数据
     * @return 保存后的故障模式（含RPN计算）
     */
    public FailureMode saveFailureMode(FailureMode failureMode) {
        if (failureMode.getFailureModeId() == null || failureMode.getFailureModeId().isEmpty()) {
            failureMode.setFailureModeId("FM_" + System.currentTimeMillis());
        }

        // 计算 RPN
        int rpn = calculateRPN(
            failureMode.getSeverityClass(),
            failureMode.getOccurrenceLevel(),
            failureMode.getDetectionLevel()
        );
        failureMode.setRpn(rpn);

        failureMode.setCreatedAt(Instant.now());
        failureMode.setUpdatedAt(Instant.now());

        failureModeStore.put(failureMode.getFailureModeId(), failureMode);
        
        log.info("故障模式保存成功: id={}, name={}, RPN={}", 
            failureMode.getFailureModeId(), failureMode.getName(), rpn);
        
        return failureMode;
    }

    /**
     * 批量保存故障模式
     */
    public List<FailureMode> saveBatch(List<FailureMode> failureModes) {
        return failureModes.stream()
            .map(this::saveFailureMode)
            .collect(Collectors.toList());
    }

    /**
     * 根据ID获取故障模式
     */
    public Optional<FailureMode> getById(String failureModeId) {
        return Optional.ofNullable(failureModeStore.get(failureModeId));
    }

    /**
     * 根据设备ID查询故障模式
     */
    public List<FailureMode> getByEquipmentId(String equipmentId) {
        return failureModeStore.values().stream()
            .filter(fm -> equipmentId.equals(fm.getEquipmentId()))
            .collect(Collectors.toList());
    }

    /**
     * 根据组件ID查询故障模式
     */
    public List<FailureMode> getByComponentId(String componentId) {
        return failureModeStore.values().stream()
            .filter(fm -> componentId.equals(fm.getComponentId()))
            .collect(Collectors.toList());
    }

    /**
     * 获取高风险故障模式（RPN排序）
     * 
     * @param limit 返回数量
     * @return 按RPN降序排列的故障模式
     */
    public List<FailureMode> getHighRiskModes(int limit) {
        return failureModeStore.values().stream()
            .sorted((a, b) -> Integer.compare(b.getRpn(), a.getRpn()))
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * 获取RPN分布统计
     */
    public Map<String, Object> getRPNStatistics() {
        List<Integer> rpns = failureModeStore.values().stream()
            .map(FailureMode::getRpn)
            .collect(Collectors.toList());

        if (rpns.isEmpty()) {
            return Map.of("count", 0, "avgRpn", 0, "maxRpn", 0, "minRpn", 0);
        }

        int max = rpns.stream().mapToInt(Integer::intValue).max().orElse(0);
        int min = rpns.stream().mapToInt(Integer::intValue).min().orElse(0);
        double avg = rpns.stream().mapToInt(Integer::intValue).average().orElse(0);

        // 风险等级分布
        long highRisk = rpns.stream().filter(rpn -> rpn >= 100).count();
        long mediumRisk = rpns.stream().filter(rpn -> rpn >= 50 && rpn < 100).count();
        long lowRisk = rpns.stream().filter(rpn -> rpn < 50).count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("count", rpns.size());
        stats.put("avgRpn", Math.round(avg * 100.0) / 100.0);
        stats.put("maxRpn", max);
        stats.put("minRpn", min);
        stats.put("highRiskCount", highRisk);
        stats.put("mediumRiskCount", mediumRisk);
        stats.put("lowRiskCount", lowRisk);

        return stats;
    }

    /**
     * 删除故障模式
     */
    public boolean deleteFailureMode(String failureModeId) {
        if (failureModeStore.containsKey(failureModeId)) {
            failureModeStore.remove(failureModeId);
            log.info("故障模式删除成功: id={}", failureModeId);
            return true;
        }
        return false;
    }

    /**
     * 获取所有故障模式
     */
    public List<FailureMode> getAllFailureModes() {
        return new ArrayList<>(failureModeStore.values());
    }

    /**
     * 搜索故障模式
     */
    public List<FailureMode> search(String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        return failureModeStore.values().stream()
            .filter(fm -> 
                (fm.getName() != null && fm.getName().toLowerCase().contains(lowerKeyword)) ||
                (fm.getDescription() != null && fm.getDescription().toLowerCase().contains(lowerKeyword)) ||
                (fm.getEquipmentId() != null && fm.getEquipmentId().toLowerCase().contains(lowerKeyword))
            )
            .collect(Collectors.toList());
    }

    // ==================== 私有方法 ====================

    /**
     * 计算 RPN（Risk Priority Number）
     * 
     * RPN = 严酷度(S) × 发生度(O) × 检测度(D)
     * 
     * @param severity 严酷度 1-4
     * @param occurrence 发生度 A-E
     * @param detection 检测度 1-5
     * @return RPN 值
     */
    private int calculateRPN(Integer severity, String occurrence, Integer detection) {
        int s = severity != null ? severity : 1;
        int o = convertOccurrenceLevel(occurrence);
        int d = detection != null ? detection : 1;
        
        return s * o * d;
    }

    /**
     * 转换发生概率等级为数值
     * A=5, B=4, C=3, D=2, E=1
     */
    private int convertOccurrenceLevel(String level) {
        if (level == null) return 1;
        return switch (level.toUpperCase()) {
            case "A" -> 5;
            case "B" -> 4;
            case "C" -> 3;
            case "D" -> 2;
            case "E" -> 1;
            default -> 1;
        };
    }
}
