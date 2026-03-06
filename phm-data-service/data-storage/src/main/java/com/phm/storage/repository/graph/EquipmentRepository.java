package com.phm.storage.repository.graph;

import com.phm.storage.entity.graph.EquipmentNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 设备节点 Repository（Neo4j）
 * 
 * TODO: 待Neo4j索引优化
 * - 考虑添加自定义查询以支持复杂图谱遍历
 * - 考虑使用投影（Projection）优化查询性能
 * - 考虑添加异步查询方法
 */
@Repository
public interface EquipmentRepository extends Neo4jRepository<EquipmentNode, Long> {
    
    /**
     * 按业务ID查找设备
     * 
     * @param equipmentId 业务设备ID
     * @return 设备节点
     */
    Optional<EquipmentNode> findByEquipmentId(String equipmentId);
    
    /**
     * 按名称查找设备
     * 
     * TODO: 待优化 - 使用全文索引提升搜索性能
     * 
     * @param name 设备名称
     * @return 设备节点
     */
    Optional<EquipmentNode> findByName(String name);
    
    /**
     * 按类型查找设备列表
     * 
     * @param type 设备类型
     * @return 设备列表
     */
    List<EquipmentNode> findByType(String type);
    
    /**
     * 按状态查找设备列表
     * 
     * @param status 设备状态
     * @return 设备列表
     */
    List<EquipmentNode> findByStatus(String status);
    
    /**
     * 查找设备及其所有组件（深度为1的图谱查询）
     * 
     * TODO: 待优化 - 使用Cypher查询控制遍历深度和返回字段
     * 
     * @param equipmentId 设备ID
     * @return 设备节点（包含组件）
     */
    @Query("MATCH (e:Equipment {id: $equipmentId})-[:HAS_COMPONENT]->(c:Component) RETURN e, collect(c) as components")
    Optional<EquipmentNode> findEquipmentWithComponents(@Param("equipmentId") String equipmentId);
    
    /**
     * 查找指定位置的所有设备
     * 
     * @param location 位置
     * @return 设备列表
     */
    List<EquipmentNode> findByLocation(String location);
}
