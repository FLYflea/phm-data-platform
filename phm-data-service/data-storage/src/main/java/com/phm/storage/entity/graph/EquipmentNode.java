package com.phm.storage.entity.graph;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * P1: 设备节点实体（知识图谱）
 * 
 * P1: 十字邻接链表 - Neo4j原生存储，自研结构接口预留
 * P1: 子图聚簇 - Neo4j索引优化，自定义聚簇策略接口预留
 * 
 * TODO: 待Neo4j索引优化
 * - 建议创建复合索引：CREATE INDEX equipment_name_type FOR (e:Equipment) ON (e.name, e.type);
 * - 考虑添加全文索引以支持设备名称搜索
 * - 考虑添加约束：CREATE CONSTRAINT equipment_id_unique FOR (e:Equipment) REQUIRE e.equipmentId IS UNIQUE;
 */
@Data
@Node("Equipment")
public class EquipmentNode {
    
    /**
     * 内部Neo4j ID（自动生成）
     */
    @org.springframework.data.annotation.Id
    @org.springframework.data.neo4j.core.schema.GeneratedValue
    private Long id;
    
    /**
     * 业务设备ID（唯一标识）
     */
    @Property("equipmentId")
    private String equipmentId;
    
    @Property("name")
    private String name;
    
    @Property("type")
    private String type;
    
    /**
     * 部署信息（JSON格式）
     * 包含：位置、部门、部署时间等
     */
    @Property("deployment")
    private String deployment;
    
    @Property("description")
    private String description;
    
    @Property("location")
    private String location;
    
    @Property("status")
    private String status;
    
    /**
     * 设备包含的组件（HAS_COMPONENT关系）
     * P1: 十字邻接链表 - Neo4j原生关系存储
     */
    @Relationship(type = "HAS_COMPONENT", direction = Relationship.Direction.OUTGOING)
    private List<ComponentNode> components = new ArrayList<>();
    
    /**
     * 设备间关联关系（RELATED_TO关系）
     * P1: 关系属性存储 - 支持relationType, strength, description等
     */
    @Relationship(type = "RELATED_TO", direction = Relationship.Direction.OUTGOING)
    private List<EquipmentNode> relatedEquipments = new ArrayList<>();
    
    /**
     * 关系属性映射（用于存储RELATED_TO关系的属性）
     * Key: 目标设备ID, Value: 关系属性Map
     */
    private Map<String, Map<String, Object>> relationProperties = new HashMap<>();
    
    /**
     * 添加组件
     * @param component 组件节点
     */
    public void addComponent(ComponentNode component) {
        this.components.add(component);
    }
    
    /**
     * 添加关联设备
     * @param equipment 关联设备节点
     */
    public void addRelatedEquipment(EquipmentNode equipment) {
        this.relatedEquipments.add(equipment);
    }
    
    /**
     * 设置关系属性
     * @param targetEquipmentId 目标设备ID
     * @param properties 属性Map
     */
    public void setRelationProperty(String targetEquipmentId, Map<String, Object> properties) {
        this.relationProperties.put(targetEquipmentId, properties);
    }
}
