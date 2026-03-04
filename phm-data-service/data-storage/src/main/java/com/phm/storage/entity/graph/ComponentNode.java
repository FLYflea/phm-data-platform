package com.phm.storage.entity.graph;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

/**
 * 组件节点实体（知识图谱）
 * 
 * TODO: 待Neo4j索引优化
 * - 建议创建索引：CREATE INDEX component_name FOR (c:Component) ON (c.name);
 * - 考虑添加与传感器数据的关系，实现图谱-时序数据联动查询
 */
@Data
@Node("Component")
public class ComponentNode {
    
    @Id
    private String id;
    
    @Property("name")
    private String name;
    
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
}
