package com.phm.storage.controller;

import com.phm.storage.entity.graph.ComponentNode;
import com.phm.storage.entity.graph.EquipmentNode;
import com.phm.storage.entity.timeseries.SensorTimeSeries;
import com.phm.storage.service.DocumentService;
import com.phm.storage.service.KnowledgeGraphService;
import com.phm.storage.service.TimeSeriesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * P0/P1/P2/P3: 数据存储控制器
 * 
 * P0: 时序数据 REST 接口（已完成）
 * - 单条/批量时序数据保存
 * - 时间范围查询
 * - 聚合统计查询
 * 
 * P1: 图数据 REST 接口（已完成）
 * - 设备/组件节点保存
 * - 关系创建
 * - 图谱查询与路径分析
 * 
 * P2: 文档存储接口（已完成）
 * - 文档保存与搜索
 * 
 * P3: 版本控制接口（预留）
 * - 快照、历史记录（待实现）
 * 
 * TODO: 待生产优化
 * - 考虑添加数据校验和清洗逻辑
 * - 考虑添加异步批量写入以提升吞吐量
 * - 考虑添加数据压缩和归档策略
 * - 考虑添加写入限流和背压机制
 */
@Slf4j
@RestController
@RequestMapping("/storage")
@RequiredArgsConstructor
public class StorageController {
    
    private final TimeSeriesService timeSeriesService;
    private final KnowledgeGraphService knowledgeGraphService;
    private final DocumentService documentService;
    
    // ==================== P0: 时序数据接口 ====================
    
    /**
     * P0: 单条保存传感器时序数据
     * 
     * @param data 传感器数据
     * @return 统一响应格式 { "id": xxx, "status": "success" }
     */
    @PostMapping("/timeseries/save")
    public Map<String, Object> saveSingle(@RequestBody SensorTimeSeries data) {
        log.info("P0保存单条传感器数据: deviceId={}, sensorType={}, timestamp={}", 
                data.getDeviceId(), data.getSensorType(), data.getTimestamp());
        
        SensorTimeSeries saved = timeSeriesService.save(data);
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", saved.getId());
        response.put("status", "success");
        response.put("deviceId", saved.getDeviceId());
        response.put("timestamp", saved.getTimestamp());
        
        log.info("P0单条数据保存成功: id={}", saved.getId());
        return response;
    }
    
    /**
     * P0: 批量保存传感器时序数据
     * 
     * @param dataList 传感器数据列表
     * @return 统一响应格式 { "savedCount": xxx }
     */
    @PostMapping("/timeseries/batch")
    public Map<String, Object> saveBatch(@RequestBody List<SensorTimeSeries> dataList) {
        log.info("P0批量保存传感器数据，条数: {}", dataList.size());
        
        List<SensorTimeSeries> savedList = timeSeriesService.saveBatch(dataList);
        
        Map<String, Object> response = new HashMap<>();
        response.put("savedCount", savedList.size());
        response.put("status", "success");
        
        log.info("P0批量数据保存成功，条数: {}", savedList.size());
        return response;
    }
    
    /**
     * P0: 按设备ID和时间范围查询传感器数据
     * 
     * @param deviceId 设备ID
     * @param start 开始时间（ISO-8601格式）
     * @param end 结束时间（ISO-8601格式）
     * @param sensorType 传感器类型（可选）
     * @return 统一响应格式 { "data": [...], "count": xxx }
     */
    @GetMapping("/timeseries/query")
    public Map<String, Object> queryByDeviceAndTimeRange(
            @RequestParam(name = "deviceId") String deviceId,
            @RequestParam(name = "start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam(name = "end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end,
            @RequestParam(name = "sensorType", required = false) String sensorType) {
        
        log.info("P0查询传感器数据: deviceId={}, sensorType={}, start={}, end={}", 
                deviceId, sensorType, start, end);
        
        List<SensorTimeSeries> result;
        if (sensorType != null && !sensorType.isEmpty()) {
            result = timeSeriesService.queryByDeviceAndSensorType(deviceId, sensorType, start, end);
        } else {
            result = timeSeriesService.queryByTimeRange(deviceId, start, end);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("data", result);
        response.put("count", result.size());
        response.put("deviceId", deviceId);
        response.put("status", "success");
        
        log.info("P0查询完成，返回数据条数: {}", result.size());
        return response;
    }
    
    /**
     * P0: 聚合统计查询
     * 
     * @param deviceId 设备ID
     * @param start 开始时间（ISO-8601格式）
     * @param end 结束时间（ISO-8601格式）
     * @param interval 时间间隔（hour/day，默认hour）
     * @return 统一响应格式 { "aggregations": [...], "interval": "hour" }
     */
    @GetMapping("/timeseries/aggregate")
    public Map<String, Object> aggregateByInterval(
            @RequestParam(name = "deviceId") String deviceId,
            @RequestParam(name = "start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam(name = "end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end,
            @RequestParam(name = "interval", defaultValue = "hour") String interval) {
        
        log.info("P0聚合统计查询: deviceId={}, interval={}, start={}, end={}", 
                deviceId, interval, start, end);
        
        List<Map<String, Object>> aggregations;
        
        // P0: 目前仅支持hour级别聚合，day级别可扩展
        if ("hour".equalsIgnoreCase(interval)) {
            aggregations = timeSeriesService.aggregateByHour(deviceId, start, end);
        } else {
            // TODO: 待扩展 - day级别聚合
            log.warn("不支持的聚合间隔: {}，回退到hour级别", interval);
            aggregations = timeSeriesService.aggregateByHour(deviceId, start, end);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("aggregations", aggregations);
        response.put("interval", interval);
        response.put("deviceId", deviceId);
        response.put("count", aggregations.size());
        response.put("status", "success");
        
        log.info("P0聚合统计完成，返回 {} 个时间段数据", aggregations.size());
        return response;
    }
    
    // ==================== P1: 图数据接口（已完成） ====================
    
    /**
     * P1: 保存设备节点
     * 
     * @param equipment 设备节点信息
     * @return 统一响应格式 { "equipmentId": xxx, "status": "success" }
     */
    @PostMapping("/graph/equipment")
    public Map<String, Object> saveEquipment(@RequestBody EquipmentNode equipment) {
        log.info("P1保存设备: equipmentId={}, name={}", 
                equipment.getEquipmentId(), equipment.getName());
        
        EquipmentNode saved = knowledgeGraphService.saveEquipment(equipment);
        
        Map<String, Object> response = new HashMap<>();
        response.put("equipmentId", saved.getEquipmentId());
        response.put("id", saved.getId());
        response.put("status", "success");
        
        log.info("P1设备保存成功: equipmentId={}", saved.getEquipmentId());
        return response;
    }
    
    /**
     * P1: 保存组件并关联到设备
     * 
     * @param equipmentId 设备ID
     * @param component 组件信息
     * @return 统一响应格式 { "componentId": xxx, "equipmentId": xxx, "status": "success" }
     */
    @PostMapping("/graph/component")
    public Map<String, Object> saveComponent(
            @RequestParam(name = "equipmentId") String equipmentId,
            @RequestBody ComponentNode component) {
        
        log.info("P1保存组件: equipmentId={}, componentId={}, name={}",
                equipmentId, component.getComponentId(), component.getName());
        
        ComponentNode saved = knowledgeGraphService.saveComponent(equipmentId, component);
        
        Map<String, Object> response = new HashMap<>();
        response.put("componentId", saved.getComponentId());
        response.put("equipmentId", equipmentId);
        response.put("id", saved.getId());
        response.put("status", "success");
        
        log.info("P1组件保存成功: componentId={}", saved.getComponentId());
        return response;
    }
    
    /**
     * P1: 创建部件间关系
     * 
     * @param fromComponentId 源组件ID
     * @param toComponentId 目标组件ID
     * @param relationType 关系类型（COMPONENT_RELATED等）
     * @param properties 关系属性（可选）
     * @return 统一响应格式 { "status": "success" }
     */
    @PostMapping("/graph/relation")
    public Map<String, Object> createRelation(
            @RequestParam(name = "fromComponentId") String fromComponentId,
            @RequestParam(name = "toComponentId") String toComponentId,
            @RequestParam(name = "relationType") String relationType,
            @RequestBody(required = false) Map<String, Object> properties) {
        
        log.info("P1创建关系: {} -> {} [{}]", fromComponentId, toComponentId, relationType);
        
        knowledgeGraphService.createRelation(fromComponentId, toComponentId, relationType, properties);
        
        Map<String, Object> response = new HashMap<>();
        response.put("fromId", fromComponentId);
        response.put("toId", toComponentId);
        response.put("relationType", relationType);
        response.put("status", "success");
        
        log.info("P1关系创建成功: {} -> {}", fromComponentId, toComponentId);
        return response;
    }
    
    /**
     * P1: 查询设备及其所有部件
     * 
     * @param equipmentId 设备ID
     * @return 统一响应格式 { "equipment": {...}, "components": [...], "status": "success" }
     */
    @GetMapping("/graph/equipment/{equipmentId}")
    public Map<String, Object> getEquipmentWithComponents(
            @PathVariable(name = "equipmentId") String equipmentId) {
        
        log.info("P1查询设备及其部件: equipmentId={}", equipmentId);
        
        Optional<EquipmentNode> result = knowledgeGraphService.findEquipmentWithComponents(equipmentId);
        
        Map<String, Object> response = new HashMap<>();
        if (result.isPresent()) {
            EquipmentNode equipment = result.get();
            response.put("equipment", equipment);
            response.put("components", equipment.getComponents());
            response.put("componentCount", equipment.getComponents().size());
            response.put("status", "success");
            log.info("P1查询成功: 设备 {} 包含 {} 个组件", 
                    equipmentId, equipment.getComponents().size());
        } else {
            response.put("status", "not_found");
            response.put("message", "设备不存在: " + equipmentId);
            log.warn("P1查询失败: 设备不存在 {}", equipmentId);
        }
        
        return response;
    }
    
    /**
     * P1: 查询组件间关联路径
     * 
     * @param startId 起始组件ID
     * @param endId 目标组件ID
     * @param maxDepth 最大深度（默认3）
     * @return 统一响应格式 { "paths": [...], "pathCount": xxx, "status": "success" }
     */
    @GetMapping("/graph/path")
    public Map<String, Object> findPath(
            @RequestParam(name = "startId") String startId,
            @RequestParam(name = "endId") String endId,
            @RequestParam(name = "maxDepth", defaultValue = "3") int maxDepth) {
        
        log.info("P1查询路径: {} -> {}, maxDepth={}", startId, endId, maxDepth);
        
        List<Map<String, Object>> paths = knowledgeGraphService.findPath(startId, endId, maxDepth);
        
        Map<String, Object> response = new HashMap<>();
        response.put("paths", paths);
        response.put("pathCount", paths.size());
        response.put("startId", startId);
        response.put("endId", endId);
        response.put("maxDepth", maxDepth);
        response.put("status", "success");
        
        log.info("P1路径查询完成: 找到 {} 条路径", paths.size());
        return response;
    }
    
    // ==================== P2: 文档存储接口（已完成） ====================
    
    /**
     * P2: 保存文档
     * 
     * @param request 包含 docId, title, content, type, metadata
     * @return 统一响应格式 { "docId": xxx, "status": "success" }
     */
    @PostMapping("/document")
    public Map<String, Object> saveDocument(@RequestBody Map<String, Object> request) {
        String docId = (String) request.get("docId");
        String title = (String) request.get("title");
        String content = (String) request.get("content");
        String type = (String) request.get("type");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) request.get("metadata");
        
        log.info("P2保存文档: docId={}, title={}, type={}", docId, title, type);
        
        DocumentService.Document saved = documentService.saveDocument(docId, title, content, type, metadata);
        
        Map<String, Object> response = new HashMap<>();
        if (saved != null) {
            response.put("docId", saved.getDocId());
            response.put("title", saved.getTitle());
            response.put("type", saved.getType());
            response.put("createdAt", saved.getCreatedAt());
            response.put("status", "success");
            log.info("P2文档保存成功: docId={}", docId);
        } else {
            response.put("status", "error");
            response.put("message", "文档保存失败");
            log.error("P2文档保存失败: docId={}", docId);
        }
        
        return response;
    }
    
    /**
     * P2: 搜索文档
     * 
     * @param keyword 关键词
     * @return 统一响应格式 { "documents": [...], "count": xxx, "status": "success" }
     */
    @GetMapping("/document/search")
    public Map<String, Object> searchDocuments(@RequestParam(name = "keyword") String keyword) {
        log.info("P2搜索文档: keyword={}", keyword);
        
        List<DocumentService.Document> results = documentService.searchDocuments(keyword);
        
        Map<String, Object> response = new HashMap<>();
        response.put("documents", results);
        response.put("count", results.size());
        response.put("keyword", keyword);
        response.put("status", "success");
        
        log.info("P2文档搜索完成: 找到 {} 个匹配", results.size());
        return response;
    }
    
    // ==================== P3: 版本控制接口（预留） ====================
    
    /**
     * P3: 创建快照（接口预留）
     * 
     * @return 预留提示
     */
    @PostMapping("/version/snapshot")
    public Map<String, Object> createSnapshot() {
        log.info("P3创建快照接口调用（预留）");
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "reserved");
        response.put("note", "版本控制待实现");
        response.put("plannedFeatures", Arrays.asList(
            "数据版本快照",
            "增量备份",
            "时间点恢复",
            "版本对比"
        ));
        
        return response;
    }
    
    /**
     * P3: 查询版本历史（接口预留）
     * 
     * @return 预留提示
     */
    @GetMapping("/version/history")
    public Map<String, Object> getVersionHistory() {
        log.info("P3查询版本历史接口调用（预留）");
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "reserved");
        response.put("note", "版本历史待实现");
        response.put("plannedFeatures", Arrays.asList(
            "历史版本列表",
            "版本详情查看",
            "版本回滚",
            "变更记录追踪"
        ));
        
        return response;
    }
    
    // ==================== 健康检查接口 ====================
    
    /**
     * 服务健康检查 - 返回支持功能和预留接口
     * 
     * @return 健康状态和功能列表
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        log.debug("健康检查请求");
        
        Map<String, Object> response = new HashMap<>();
        response.put("service", "data-storage");
        response.put("port", 8103);
        response.put("status", "UP");
        response.put("timestamp", Instant.now().toString());
        
        // 已实现功能（P0/P1/P2）
        List<Map<String, Object>> features = new ArrayList<>();
        features.add(buildFeature("timeseries", "时序数据存储", "P0", true));
        features.add(buildFeature("graph", "图数据存储", "P1", true));
        features.add(buildFeature("document", "文档存储", "P2", true));
        response.put("features", features);
        
        // 预留接口（P3）
        List<Map<String, Object>> reserved = new ArrayList<>();
        reserved.add(buildReservedFeature("custom-storage-engine", "自定义存储引擎", "P3"));
        reserved.add(buildReservedFeature("custom-index", "自定义索引", "P3"));
        reserved.add(buildReservedFeature("version-control", "版本控制", "P3"));
        response.put("reserved", reserved);
        
        response.put("note", "data-storage服务运行正常");
        return response;
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 构建功能信息
     */
    private Map<String, Object> buildFeature(String name, String description, String level, boolean available) {
        Map<String, Object> feature = new HashMap<>();
        feature.put("name", name);
        feature.put("description", description);
        feature.put("level", level);
        feature.put("available", available);
        return feature;
    }
    
    /**
     * 构建预留功能信息
     */
    private Map<String, Object> buildReservedFeature(String name, String description, String level) {
        Map<String, Object> feature = new HashMap<>();
        feature.put("name", name);
        feature.put("description", description);
        feature.put("level", level);
        feature.put("status", "reserved");
        return feature;
    }
}
