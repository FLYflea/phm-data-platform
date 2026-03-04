package com.phm.storage.entity.graph;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备节点实体（知识图谱）
 * 
 * TODO: 待Neo4j索引优化
 * - 建议创建复合索引：CREATE INDEX equipment_name_type FOR (e:Equipment) ON (e.name, e.type);
 * - 考虑添加全文索引以支持设备名称搜索
 * - 考虑添加约束：CREATE CONSTRAINT equipment_id_unique FOR (e:Equipment) REQUIRE e.id IS UNIQUE;
 */
@Data
@Node("Equipment")
public class EquipmentNode {
    
    @Id
    private String id;
    
    @Property("name")
    private String name;
    
    @Property("type")
    private String type;
    
    @Property("description")
    private String description;
    
    @Property("location")
    private String location;
    
    @Property("status")
    private String status;
    
    /**
     * 设备包含的组件（关系）
     * TODO: 考虑添加关系属性，如安装日期、维护周期等
     */
    @Relationship(type = "HAS_COMPONENT", direction = Relationship.Direction.OUTGOING)
    private List<ComponentNode> components = new ArrayList<>();
    
    /**
     * 添加组件
     * @param component 组件节点
     */
    public void addComponent(ComponentNode component) {
        this.components.add(component);
    }
}
