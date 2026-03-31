package com.phm.storage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phm.storage.entity.graph.ComponentNode;
import com.phm.storage.entity.graph.EquipmentNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.types.Node;
import org.springframework.stereotype.Service;

import java.util.*;

import static org.neo4j.driver.Values.parameters;

/**
 * P1: 知识图谱服务（使用 Neo4j 原生驱动）
 *
 * 使用原生驱动避免 Spring Data Neo4j 与 JPA 的事务管理器冲突
 *
 * 功能：
 * - 设备/组件节点保存
 * - 关系创建（HAS_COMPONENT, RELATED_TO）
 * - 图谱查询（设备、组件、路径）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphService {

    private final Driver neo4jDriver;
    private final ObjectMapper objectMapper;

    /**
     * P1: 保存设备节点
     */
    public EquipmentNode saveEquipment(EquipmentNode equipment) {
        if (equipment == null || equipment.getEquipmentId() == null) {
            log.warn("保存设备失败：设备或设备ID为空");
            return null;
        }

        log.info("P1保存设备: equipmentId={}, name={}", equipment.getEquipmentId(), equipment.getName());

        try (Session session = neo4jDriver.session()) {
            Map<String, Object> result = session.executeWrite(tx -> {
                var query = tx.run(
                    "MERGE (e:Equipment {equipmentId: $eqId}) " +
                    "SET e.name = $name, e.type = $type, e.location = $location, " +
                    "    e.status = $status, e.description = $description " +
                    "RETURN id(e) as nodeId, e.equipmentId as equipmentId, " +
                    "       e.name as name, e.type as type",
                    parameters(
                        "eqId", equipment.getEquipmentId(),
                        "name", equipment.getName(),
                        "type", equipment.getType(),
                        "location", equipment.getLocation(),
                        "status", equipment.getStatus(),
                        "description", equipment.getDescription()
                    )
                );
                return query.single().asMap();
            });

            equipment.setId(((Number) result.get("nodeId")).longValue());
            log.info("P1设备保存成功: id={}, equipmentId={}", equipment.getId(), equipment.getEquipmentId());
            return equipment;
        } catch (Exception e) {
            log.error("P1设备保存失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * P1: 保存组件并关联到设备
     */
    public ComponentNode saveComponent(String equipmentId, ComponentNode component) {
        if (equipmentId == null || component == null || component.getComponentId() == null) {
            log.warn("保存组件失败：参数为空");
            return null;
        }

        log.info("P1保存组件: equipmentId={}, componentId={}, name={}",
                equipmentId, component.getComponentId(), component.getName());

        try (Session session = neo4jDriver.session()) {
            Map<String, Object> result = session.executeWrite(tx -> {
                var query = tx.run(
                    "MATCH (e:Equipment {equipmentId: $eqId}) " +
                    "MERGE (c:Component {componentId: $compId}) " +
                    "SET c.name = $name, c.type = $type, c.description = $description " +
                    "MERGE (e)-[:HAS_COMPONENT]->(c) " +
                    "RETURN id(c) as nodeId, c.componentId as componentId, c.name as name",
                    parameters(
                        "eqId", equipmentId,
                        "compId", component.getComponentId(),
                        "name", component.getName(),
                        "type", component.getType(),
                        "description", component.getDescription()
                    )
                );
                return query.single().asMap();
            });

            component.setId(((Number) result.get("nodeId")).longValue());
            log.info("P1组件保存成功: id={}, componentId={}", component.getId(), component.getComponentId());
            return component;
        } catch (Exception e) {
            log.error("P1组件保存失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * P1: 创建关系
     */
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

        String cypher;
        String label;
        String idField;

        switch (relationType.toUpperCase()) {
            case "EQUIPMENT_RELATED":
                label = "Equipment";
                idField = "equipmentId";
                cypher = "MATCH (a:" + label + " {" + idField + ": $fromId}) " +
                        "MATCH (b:" + label + " {" + idField + ": $toId}) " +
                        "MERGE (a)-[r:RELATED_TO]->(b) " +
                        "SET r.type = $type, r.properties = $props " +
                        "RETURN r";
                break;
            case "COMPONENT_RELATED":
                label = "Component";
                idField = "componentId";
                cypher = "MATCH (a:" + label + " {" + idField + ": $fromId}) " +
                        "MATCH (b:" + label + " {" + idField + ": $toId}) " +
                        "MERGE (a)-[r:RELATED_TO]->(b) " +
                        "SET r.type = $type, r.properties = $props " +
                        "RETURN r";
                break;
            case "HAS_COMPONENT":
                cypher = "MATCH (e:Equipment {equipmentId: $fromId}) " +
                        "MATCH (c:Component {componentId: $toId}) " +
                        "MERGE (e)-[r:HAS_COMPONENT]->(c) " +
                        "SET r.properties = $props " +
                        "RETURN r";
                break;
            default:
                log.warn("未知的关系类型: {}", relationType);
                return;
        }

                // 创建 final 副本供 lambda 使用
        final String finalPropsJson = propsJson;

        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                tx.run(cypher, parameters(
                    "fromId", fromId,
                    "toId", toId,
                    "type", relationType,
                    "props", finalPropsJson
                ));
                return null;
            });
            log.info("P1关系创建成功: {} -> {} [{}]", fromId, toId, relationType);
        } catch (Exception e) {
            log.error("P1关系创建失败: {}", e.getMessage(), e);
        }
    }

    /**
     * P1: 查询设备（带组件）
     */
    public Optional<EquipmentNode> findEquipmentWithComponents(String equipmentId) {
        if (equipmentId == null) {
            return Optional.empty();
        }

        log.debug("P1查询设备（带组件）: equipmentId={}", equipmentId);

        try (Session session = neo4jDriver.session()) {
            return session.executeRead(tx -> {
                var result = tx.run(
                    "MATCH (e:Equipment {equipmentId: $eqId}) " +
                    "OPTIONAL MATCH (e)-[:HAS_COMPONENT]->(c:Component) " +
                    "RETURN e, collect(c) as components",
                    parameters("eqId", equipmentId)
                );

                if (result.hasNext()) {
                    Record record = result.next();
                    return Optional.of(mapToEquipmentNode(record));
                }
                return Optional.empty();
            });
        } catch (Exception e) {
            log.error("P1查询设备失败: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * P1: 查询设备完整图谱（组件+关联设备）
     */
    public Optional<EquipmentNode> findEquipmentFullGraph(String equipmentId) {
        if (equipmentId == null) {
            return Optional.empty();
        }

        log.debug("P1查询设备完整图谱: equipmentId={}", equipmentId);

        try (Session session = neo4jDriver.session()) {
            return session.executeRead(tx -> {
                var result = tx.run(
                    "MATCH (e:Equipment {equipmentId: $eqId}) " +
                    "OPTIONAL MATCH (e)-[:HAS_COMPONENT]->(c:Component) " +
                    "OPTIONAL MATCH (e)-[:RELATED_TO]->(re:Equipment) " +
                    "RETURN e, collect(DISTINCT c) as components, collect(DISTINCT re) as relatedEquipments",
                    parameters("eqId", equipmentId)
                );

                if (result.hasNext()) {
                    Record record = result.next();
                    return Optional.of(mapToEquipmentNode(record));
                }
                return Optional.empty();
            });
        } catch (Exception e) {
            log.error("P1查询设备图谱失败: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * P1: 查询组件间路径
     */
    public List<Map<String, Object>> findPath(String startId, String endId, int depth) {
        if (startId == null || endId == null || depth <= 0) {
            log.warn("查询路径失败：参数无效");
            return Collections.emptyList();
        }

        log.info("P1查询组件路径: {} -> {}, depth={}", startId, endId, depth);

        try (Session session = neo4jDriver.session()) {
            return session.executeRead(tx -> {
                var result = tx.run(
                    "MATCH path = (c1:Component {componentId: $startId})-[:RELATED_TO*1.." + depth + "]-(c2:Component {componentId: $endId}) " +
                    "RETURN path ORDER BY length(path) LIMIT 5",
                    parameters("startId", startId, "endId", endId)
                );

                List<Map<String, Object>> paths = new ArrayList<>();
                while (result.hasNext()) {
                    paths.add(result.next().asMap());
                }
                log.info("P1路径查询完成，找到 {} 条路径", paths.size());
                return paths;
            });
        } catch (Exception e) {
            log.error("P1查询路径失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * P1: 查询最短路径
     */
    public Optional<Map<String, Object>> findShortestPath(String startId, String endId, int maxDepth) {
        if (startId == null || endId == null || maxDepth <= 0) {
            log.warn("查询最短路径失败：参数无效");
            return Optional.empty();
        }

        log.info("P1查询最短路径: {} -> {}, maxDepth={}", startId, endId, maxDepth);

        try (Session session = neo4jDriver.session()) {
            return session.executeRead(tx -> {
                var result = tx.run(
                    "MATCH (c1:Component {componentId: $startId}), (c2:Component {componentId: $endId}) " +
                    "MATCH path = shortestPath((c1)-[:RELATED_TO*1.." + maxDepth + "]-(c2)) " +
                    "RETURN path",
                    parameters("startId", startId, "endId", endId)
                );

                if (result.hasNext()) {
                    log.info("P1找到最短路径");
                    return Optional.of(result.next().asMap());
                }
                log.info("P1未找到路径");
                return Optional.empty();
            });
        } catch (Exception e) {
            log.error("P1查询最短路径失败: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * P1: 查询设备邻居
     */
    public List<EquipmentNode> findNeighbors(String equipmentId, int depth) {
        if (equipmentId == null || depth <= 0) {
            return Collections.emptyList();
        }

        log.debug("P1查询设备邻居: equipmentId={}, depth={}", equipmentId, depth);

        try (Session session = neo4jDriver.session()) {
            return session.executeRead(tx -> {
                var result = tx.run(
                    "MATCH (e:Equipment {equipmentId: $eqId})-[:RELATED_TO*1.." + depth + "]-(neighbor:Equipment) " +
                    "WHERE neighbor <> e " +
                    "RETURN DISTINCT neighbor",
                    parameters("eqId", equipmentId)
                );

                List<EquipmentNode> neighbors = new ArrayList<>();
                while (result.hasNext()) {
                    Node node = result.next().get("neighbor").asNode();
                    neighbors.add(mapNodeToEquipment(node));
                }
                return neighbors;
            });
        } catch (Exception e) {
            log.error("P1查询邻居失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * P1: 按类型查找设备（type为null时返回所有设备）
     */
    public List<EquipmentNode> findByType(String type) {
        try (Session session = neo4jDriver.session()) {
            return session.executeRead(tx -> {
                String cypher;
                org.neo4j.driver.Result result;
                if (type == null || type.isEmpty()) {
                    cypher = "MATCH (e:Equipment) RETURN e";
                    result = tx.run(cypher);
                } else {
                    cypher = "MATCH (e:Equipment {type: $type}) RETURN e";
                    result = tx.run(cypher, parameters("type", type));
                }

                List<EquipmentNode> equipments = new ArrayList<>();
                while (result.hasNext()) {
                    Node node = result.next().get("e").asNode();
                    equipments.add(mapNodeToEquipment(node));
                }
                return equipments;
            });
        } catch (Exception e) {
            log.error("P1按类型查找失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * P1: 按名称模糊搜索设备
     */
    public List<EquipmentNode> searchByName(String name) {
        if (name == null || name.isEmpty()) {
            return Collections.emptyList();
        }

        try (Session session = neo4jDriver.session()) {
            return session.executeRead(tx -> {
                var result = tx.run(
                    "MATCH (e:Equipment) WHERE e.name CONTAINS $name RETURN e",
                    parameters("name", name)
                );

                List<EquipmentNode> equipments = new ArrayList<>();
                while (result.hasNext()) {
                    Node node = result.next().get("e").asNode();
                    equipments.add(mapNodeToEquipment(node));
                }
                return equipments;
            });
        } catch (Exception e) {
            log.error("P1按名称搜索失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * P1: 获取图谱统计信息
     */
    public Map<String, Object> getGraphStats() {
        log.debug("P1获取图谱统计信息");

        try (Session session = neo4jDriver.session()) {
            return session.executeRead(tx -> {
                var result = tx.run(
                    "MATCH (e:Equipment) WITH count(e) as eqCount " +
                    "MATCH (c:Component) WITH eqCount, count(c) as compCount " +
                    "MATCH ()-[r]->() WITH eqCount, compCount, count(r) as relCount " +
                    "RETURN { equipmentCount: eqCount, componentCount: compCount, relationCount: relCount } as stats"
                );

                if (result.hasNext()) {
                    return result.next().get("stats").asMap();
                }
                return Collections.emptyMap();
            });
        } catch (Exception e) {
            log.error("P1获取统计信息失败: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * P1: 删除设备（级联删除组件关系）
     */
    public void deleteEquipment(String equipmentId) {
        if (equipmentId == null) {
            return;
        }

        log.info("P1删除设备: equipmentId={}", equipmentId);

        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                tx.run(
                    "MATCH (e:Equipment {equipmentId: $eqId}) " +
                    "OPTIONAL MATCH (e)-[r]-() " +
                    "DELETE r, e",
                    parameters("eqId", equipmentId)
                );
                return null;
            });
            log.info("P1设备删除成功: equipmentId={}", equipmentId);
        } catch (Exception e) {
            log.error("P1删除设备失败: {}", e.getMessage(), e);
        }
    }

    // ==================== 私有辅助方法 ====================

    private EquipmentNode mapToEquipmentNode(Record record) {
        EquipmentNode equipment = new EquipmentNode();
        Node eNode = record.get("e").asNode();
        equipment.setId(eNode.id());
        equipment.setEquipmentId(eNode.get("equipmentId").asString());
        equipment.setName(eNode.get("name").asString());
        equipment.setType(eNode.get("type").asString());
        if (eNode.containsKey("location")) {
            equipment.setLocation(eNode.get("location").asString());
        }
        if (eNode.containsKey("status")) {
            equipment.setStatus(eNode.get("status").asString());
        }
        if (eNode.containsKey("description")) {
            equipment.setDescription(eNode.get("description").asString());
        }
        return equipment;
    }

    private EquipmentNode mapNodeToEquipment(Node node) {
        EquipmentNode equipment = new EquipmentNode();
        equipment.setId(node.id());
        equipment.setEquipmentId(node.get("equipmentId").asString());
        equipment.setName(node.get("name").asString());
        equipment.setType(node.get("type").asString());
        if (node.containsKey("location")) {
            equipment.setLocation(node.get("location").asString());
        }
        if (node.containsKey("status")) {
            equipment.setStatus(node.get("status").asString());
        }
        if (node.containsKey("description")) {
            equipment.setDescription(node.get("description").asString());
        }
        return equipment;
    }
}
