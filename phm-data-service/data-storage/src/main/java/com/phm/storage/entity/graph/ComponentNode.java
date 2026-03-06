package com.phm.storage.entity.graph;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

/**
 * P1: 组件节点实体（知识图谱）
 * 
 * P1: 十字邻接链表 - Neo4j原生存储，自研结构接口预留
 * P1: 子图聚簇 - Neo4j索引优化，自定义聚簇策略接口预留
 * 
 * TODO: 待Neo4j索引优化
 * - 建议创建索引：CREATE INDEX component_name FOR (c:Component) ON (c.name);
 * - 考虑添加与传感器数据的关系，实现图谱-时序数据联动查询
 */
@Data
@Node("Component")
public class ComponentNode {
    
    /**
     * 内部Neo4j ID（自动生成）
     */
    @org.springframework.data.annotation.Id
    @org.springframework.data.neo4j.core.schema.GeneratedValue
    private Long id;
    
    /**
     * 业务组件ID（唯一标识）
     */
    @Property("componentId")
    private String componentId;
    
    @Property("name")
    private String name;
    
    /**
     * 组件功能描述
     */
    @Property("function")
    private String function;
    
    @Property("type")
    private String type;
    
    @Property("model")
    private String model;
    
    @Property("manufacturer")
    private String manufacturer;
    
    @Property("description")
    private String description;
    
    /**
     * 所属设备（反向关系）
     */
    @Relationship(type = "HAS_COMPONENT", direction = Relationship.Direction.INCOMING)
    private EquipmentNode equipment;
    
    /**
     * 组件间关联关系（RELATED_TO关系）
     * P1: 十字邻接链表 - 支持组件间连接关系（如传动、控制等）
     */
    @Relationship(type = "RELATED_TO", direction = Relationship.Direction.OUTGOING)
    private List<ComponentNode> relatedComponents = new ArrayList<>();
    
    /**
     * 添加关联组件
     * @param component 关联组件节点
     */
    public void addRelatedComponent(ComponentNode component) {
        this.relatedComponents.add(component);
    }
}
