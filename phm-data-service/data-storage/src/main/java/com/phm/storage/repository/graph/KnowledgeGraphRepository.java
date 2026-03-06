package com.phm.storage.repository.graph;

import com.phm.storage.entity.graph.EquipmentNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * P1: 知识图谱 Repository（Neo4j）
 *
 * P1: 十字邻接链表 - Neo4j原生存储，自研结构接口预留
 * P1: 子图聚簇 - Neo4j索引优化，自定义聚簇策略接口预留
 *
 * 功能：
 * - 设备/组件节点查询
 * - 图谱路径查询
 * - 关系查询
 *
 * TODO: 待优化
 * - 使用全文索引提升搜索性能
 * - 使用投影（Projection）优化查询性能
 * - 添加异步查询方法
 */
@Repository
public interface KnowledgeGraphRepository extends Neo4jRepository<EquipmentNode, Long> {

    /**
     * P1: 按业务设备ID查找设备
     *
     * @param equipmentId 业务设备ID
     * @return 设备节点
     */
    Optional<EquipmentNode> findByEquipmentId(String equipmentId);

    /**
     * P1: 查找设备及其所有组件（HAS_COMPONENT关系）
     *
     * 使用Cypher查询一次性加载设备和组件，避免N+1问题
     *
     * @param equipmentId 业务设备ID
     * @return 设备节点（包含组件列表）
     */
    @Query("MATCH (e:Equipment {equipmentId: $eqId}) " +
           "OPTIONAL MATCH (e)-[:HAS_COMPONENT]->(c:Component) " +
           "RETURN e, collect(c) as components")
    Optional<EquipmentNode> findWithComponents(@Param("eqId") String equipmentId);

    /**
     * P1: 查找设备及其完整关系图谱（组件+关联设备）
     *
     * @param equipmentId 业务设备ID
     * @return 设备节点（包含组件和关联设备）
     */
    @Query("MATCH (e:Equipment {equipmentId: $eqId}) " +
           "OPTIONAL MATCH (e)-[:HAS_COMPONENT]->(c:Component) " +
           "OPTIONAL MATCH (e)-[:RELATED_TO]->(re:Equipment) " +
           "RETURN e, collect(DISTINCT c) as components, collect(DISTINCT re) as relatedEquipments")
    Optional<EquipmentNode> findWithFullGraph(@Param("eqId") String equipmentId);

    /**
     * P1: 查询组件间的路径（RELATED_TO关系）
     *
     * 使用可变长度路径查询，支持指定深度范围
     *
     * @param startId 起始组件ID
     * @param endId 目标组件ID
     * @param depth 最大深度
     * @return 路径列表（包含节点和关系）
     */
    @Query("MATCH path = (c1:Component {componentId: $startId})-[:RELATED_TO*1..$depth]-(c2:Component {componentId: $endId}) " +
           "RETURN path " +
           "ORDER BY length(path) " +
           "LIMIT 5")
    List<Map<String, Object>> findComponentPath(@Param("startId") String startId,
                                                @Param("endId") String endId,
                                                @Param("depth") int depth);

    /**
     * P1: 查询组件间的最短路径
     *
     * @param startId 起始组件ID
     * @param endId 目标组件ID
     * @param maxDepth 最大搜索深度
     * @return 最短路径
     */
    @Query("MATCH (c1:Component {componentId: $startId}), (c2:Component {componentId: $endId}) " +
           "MATCH path = shortestPath((c1)-[:RELATED_TO*1..$maxDepth]-(c2)) " +
           "RETURN path")
    Optional<Map<String, Object>> findShortestPath(@Param("startId") String startId,
                                                   @Param("endId") String endId,
                                                   @Param("maxDepth") int maxDepth);

    /**
     * P1: 查询设备的邻居节点（指定深度）
     *
     * @param equipmentId 设备ID
     * @param depth 深度
     * @return 邻居节点列表
     */
    @Query("MATCH (e:Equipment {equipmentId: $eqId})-[:RELATED_TO*1..$depth]-(neighbor) " +
           "WHERE neighbor <> e " +
           "RETURN DISTINCT neighbor")
    List<EquipmentNode> findNeighbors(@Param("eqId") String equipmentId,
                                      @Param("depth") int depth);

    /**
     * P1: 创建设备间关系（RELATED_TO）
     *
     * @param fromId 源设备ID
     * @param toId 目标设备ID
     * @param relationType 关系类型
     * @param properties 关系属性（JSON格式）
     */
    @Query("MATCH (e1:Equipment {equipmentId: $fromId}) " +
           "MATCH (e2:Equipment {equipmentId: $toId}) " +
           "MERGE (e1)-[r:RELATED_TO]->(e2) " +
           "SET r.type = $relationType, r.properties = $properties " +
           "RETURN r")
    void createEquipmentRelation(@Param("fromId") String fromId,
                                 @Param("toId") String toId,
                                 @Param("relationType") String relationType,
                                 @Param("properties") String properties);

    /**
     * P1: 创建组件间关系（RELATED_TO）
     *
     * @param fromId 源组件ID
     * @param toId 目标组件ID
     * @param relationType 关系类型
     * @param properties 关系属性（JSON格式）
     */
    @Query("MATCH (c1:Component {componentId: $fromId}) " +
           "MATCH (c2:Component {componentId: $toId}) " +
           "MERGE (c1)-[r:RELATED_TO]->(c2) " +
           "SET r.type = $relationType, r.properties = $properties " +
           "RETURN r")
    void createComponentRelation(@Param("fromId") String fromId,
                                 @Param("toId") String toId,
                                 @Param("relationType") String relationType,
                                 @Param("properties") String properties);

    /**
     * P1: 创建设备-组件关系（HAS_COMPONENT）
     *
     * @param equipmentId 设备ID
     * @param componentId 组件ID
     * @param properties 关系属性（JSON格式，可选）
     */
    @Query("MATCH (e:Equipment {equipmentId: $eqId}) " +
           "MATCH (c:Component {componentId: $compId}) " +
           "MERGE (e)-[r:HAS_COMPONENT]->(c) " +
           "SET r.properties = $properties " +
           "RETURN r")
    void createHasComponentRelation(@Param("eqId") String equipmentId,
                                    @Param("compId") String componentId,
                                    @Param("properties") String properties);

    /**
     * P1: 按类型查找设备
     *
     * @param type 设备类型
     * @return 设备列表
     */
    List<EquipmentNode> findByType(String type);

    /**
     * P1: 按名称模糊查找设备
     *
     * TODO: 待优化 - 使用全文索引
     *
     * @param name 设备名称（模糊匹配）
     * @return 设备列表
     */
    @Query("MATCH (e:Equipment) WHERE e.name CONTAINS $name RETURN e")
    List<EquipmentNode> findByNameContaining(@Param("name") String name);

    /**
     * P1: 统计图谱规模
     *
     * @return 统计信息 { equipmentCount, componentCount, relationCount }
     */
    @Query("MATCH (e:Equipment) WITH count(e) as eqCount " +
           "MATCH (c:Component) WITH eqCount, count(c) as compCount " +
           "MATCH ()-[r]->() WITH eqCount, compCount, count(r) as relCount " +
           "RETURN { equipmentCount: eqCount, componentCount: compCount, relationCount: relCount } as stats")
    Map<String, Object> getGraphStats();
}
