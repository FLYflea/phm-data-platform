package com.phm.storage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phm.storage.entity.graph.ComponentNode;
import com.phm.storage.entity.graph.EquipmentNode;
import com.phm.storage.repository.graph.KnowledgeGraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * P1: 知识图谱服务
 *
 * P1: 十字邻接链表 - Neo4j原生存储，自研结构接口预留
 * P1: 子图聚簇 - Neo4j索引优化，自定义聚簇策略接口预留
 *
 * 功能：
 * - 设备/组件节点保存
 * - 关系创建（HAS_COMPONENT, RELATED_TO）
 * - 图谱查询（设备、组件、路径）
 *
 * TODO: 待优化
 * - 添加缓存层（Redis）提升查询性能
 * - 支持批量导入（CSV/JSON）
 * - 添加图谱可视化数据接口
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphService {

    private final KnowledgeGraphRepository knowledgeGraphRepository;
    private final ObjectMapper objectMapper;

    /**
     * P1: 保存设备节点
     *
     * @param equipment 设备节点
     * @return 保存后的设备节点（包含生成的ID）
     */
    @Transactional
    public EquipmentNode saveEquipment(EquipmentNode equipment) {
        if (equipment == null || equipment.getEquipmentId() == null) {
            log.warn("保存设备失败：设备或设备ID为空");
            return null;
        }

        log.info("P1保存设备: equipmentId={}, name={}", equipment.getEquipmentId(), equipment.getName());

        EquipmentNode saved = knowledgeGraphRepository.save(equipment);
        log.info("P1设备保存成功: id={}, equipmentId={}", saved.getId(), saved.getEquipmentId());
        return saved;
    }

    /**
     * P1: 保存组件并关联到设备
     *
     * @param equipmentId 设备ID
     * @param component 组件节点
     * @return 保存后的组件节点
     */
    @Transactional
    public ComponentNode saveComponent(String equipmentId, ComponentNode component) {
        if (equipmentId == null || component == null || component.getComponentId() == null) {
            log.warn("保存组件失败：参数为空");
            return null;
        }

        log.info("P1保存组件: equipmentId={}, componentId={}, name={}",
                equipmentId, component.getComponentId(), component.getName());

        // 先查找设备
        Optional<EquipmentNode> equipmentOpt = knowledgeGraphRepository.findByEquipmentId(equipmentId);
        if (equipmentOpt.isEmpty()) {
            log.error("保存组件失败：设备不存在, equipmentId={}", equipmentId);
            return null;
        }

        EquipmentNode equipment = equipmentOpt.get();

        // 设置组件与设备的关系
        component.setEquipment(equipment);
        equipment.addComponent(component);

        // 保存设备（级联保存组件）
        EquipmentNode saved = knowledgeGraphRepository.save(equipment);

        // 返回保存的组件
        ComponentNode savedComponent = saved.getComponents().stream()
                .filter(c -> component.getComponentId().equals(c.getComponentId()))
                .findFirst()
                .orElse(component);

        log.info("P1组件保存成功: id={}, componentId={}", savedComponent.getId(), savedComponent.getComponentId());
        return savedComponent;
    }

    /**
     * P1: 创建关系
     *
     * 支持的关系类型：
     * - EQUIPMENT_RELATED: 设备间关联
     * - COMPONENT_RELATED: 组件间关联
     * - HAS_COMPONENT: 设备-组件关系（通常通过saveComponent创建）
     *
     * @param fromId 源节点ID
     * @param toId 目标节点ID
     * @param relationType 关系类型
     * @param properties 关系属性（可选）
     */
    @Transactional
    public void createRelation(String fromId, String toId, String relationType, Map<String, Object> properties) {
        if (fromId == null || toId == null || relationType == null) {
            log.warn("创建关系失败：参数为空");
            return;
        }

        log.info("P1创建关系: fromId={}, toId={}, type={}", fromId, toId, relationType);

        String propsJson = null;
        if (properties != null && !properties.isEmpty()) {
            try {
                propsJson = objectMapper.writeValueAsString(properties);
            } catch (JsonProcessingException e) {
                log.warn("关系属性序列化失败: {}", e.getMessage());
            }
        }

        switch (relationType.toUpperCase()) {
            case "EQUIPMENT_RELATED":
                knowledgeGraphRepository.createEquipmentRelation(fromId, toId, relationType, propsJson);
                break;
            case "COMPONENT_RELATED":
                knowledgeGraphRepository.createComponentRelation(fromId, toId, relationType, propsJson);
                break;
            case "HAS_COMPONENT":
                knowledgeGraphRepository.createHasComponentRelation(fromId, toId, propsJson);
                break;
            default:
                log.warn("未知的关系类型: {}", relationType);
                return;
        }

        log.info("P1关系创建成功: {} -> {} [{}]", fromId, toId, relationType);
    }

    /**
     * P1: 查询设备（带组件）
     *
     * @param equipmentId 设备ID
     * @return 设备节点（包含组件）
     */
    public Optional<EquipmentNode> findEquipmentWithComponents(String equipmentId) {
        if (equipmentId == null) {
            return Optional.empty();
        }

        log.debug("P1查询设备（带组件）: equipmentId={}", equipmentId);
        return knowledgeGraphRepository.findWithComponents(equipmentId);
    }

    /**
     * P1: 查询设备完整图谱（组件+关联设备）
     *
     * @param equipmentId 设备ID
     * @return 设备节点（完整图谱）
     */
    public Optional<EquipmentNode> findEquipmentFullGraph(String equipmentId) {
        if (equipmentId == null) {
            return Optional.empty();
        }

        log.debug("P1查询设备完整图谱: equipmentId={}", equipmentId);
        return knowledgeGraphRepository.findWithFullGraph(equipmentId);
    }

    /**
     * P1: 查询组件间路径
     *
     * @param startId 起始组件ID
     * @param endId 目标组件ID
     * @param depth 最大深度
     * @return 路径列表
     */
    public List<Map<String, Object>> findPath(String startId, String endId, int depth) {
        if (startId == null || endId == null || depth <= 0) {
            log.warn("查询路径失败：参数无效");
            return Collections.emptyList();
        }

        log.info("P1查询组件路径: {} -> {}, depth={}", startId, endId, depth);

        List<Map<String, Object>> paths = knowledgeGraphRepository.findComponentPath(startId, endId, depth);
        log.info("P1路径查询完成，找到 {} 条路径", paths.size());
        return paths;
    }

    /**
     * P1: 查询最短路径
     *
     * @param startId 起始组件ID
     * @param endId 目标组件ID
     * @param maxDepth 最大搜索深度
     * @return 最短路径（Optional）
     */
    public Optional<Map<String, Object>> findShortestPath(String startId, String endId, int maxDepth) {
        if (startId == null || endId == null || maxDepth <= 0) {
            log.warn("查询最短路径失败：参数无效");
            return Optional.empty();
        }

        log.info("P1查询最短路径: {} -> {}, maxDepth={}", startId, endId, maxDepth);

        Optional<Map<String, Object>> path = knowledgeGraphRepository.findShortestPath(startId, endId, maxDepth);
        if (path.isPresent()) {
            log.info("P1找到最短路径");
        } else {
            log.info("P1未找到路径");
        }
        return path;
    }

    /**
     * P1: 查询设备邻居
     *
     * @param equipmentId 设备ID
     * @param depth 深度
     * @return 邻居设备列表
     */
    public List<EquipmentNode> findNeighbors(String equipmentId, int depth) {
        if (equipmentId == null || depth <= 0) {
            return Collections.emptyList();
        }

        log.debug("P1查询设备邻居: equipmentId={}, depth={}", equipmentId, depth);
        return knowledgeGraphRepository.findNeighbors(equipmentId, depth);
    }

    /**
     * P1: 按类型查找设备
     *
     * @param type 设备类型
     * @return 设备列表
     */
    public List<EquipmentNode> findByType(String type) {
        if (type == null) {
            return Collections.emptyList();
        }
        return knowledgeGraphRepository.findByType(type);
    }

    /**
     * P1: 按名称模糊搜索设备
     *
     * @param name 设备名称（模糊匹配）
     * @return 设备列表
     */
    public List<EquipmentNode> searchByName(String name) {
        if (name == null || name.isEmpty()) {
            return Collections.emptyList();
        }
        return knowledgeGraphRepository.findByNameContaining(name);
    }

    /**
     * P1: 获取图谱统计信息
     *
     * @return 统计信息 { equipmentCount, componentCount, relationCount }
     */
    public Map<String, Object> getGraphStats() {
        log.debug("P1获取图谱统计信息");
        return knowledgeGraphRepository.getGraphStats();
    }

    /**
     * P1: 删除设备（级联删除组件关系）
     *
     * @param equipmentId 设备ID
     */
    @Transactional
    public void deleteEquipment(String equipmentId) {
        if (equipmentId == null) {
            return;
        }

        log.info("P1删除设备: equipmentId={}", equipmentId);

        Optional<EquipmentNode> equipment = knowledgeGraphRepository.findByEquipmentId(equipmentId);
        equipment.ifPresent(e -> {
            knowledgeGraphRepository.delete(e);
            log.info("P1设备删除成功: equipmentId={}", equipmentId);
        });
    }
}
