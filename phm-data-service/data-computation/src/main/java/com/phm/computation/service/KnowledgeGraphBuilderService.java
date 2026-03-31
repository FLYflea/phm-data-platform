package com.phm.computation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 知识图谱构建服务
 * 
 * 职责：
 * - 调用采集层解析图像/文本
 * - 提取设备和组件信息
 * - 构建知识图谱并保存到存储层（Neo4j）
 * 
 * 这是计算层的核心功能，体现"计算"的价值：
 * - 不是简单的数据搬运
 * - 而是理解内容、提取知识、构建关系
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphBuilderService {

    private final RestTemplate restTemplate;

    @Value("${collection.service.url:http://localhost:8101}")
    private String collectionServiceUrl;

    @Value("${storage.service.url:http://localhost:8103}")
    private String storageServiceUrl;

    /**
     * 从图像构建知识图谱
     * 
     * 流程：
     * 1. 调用采集层 /document/image 解析图像
     * 2. 提取部件信息
     * 3. 保存设备节点到 Neo4j
     * 4. 保存组件节点并建立关系
     * 
     * @param imageId 图像ID
     * @param imageFile 图像文件
     * @param equipmentId 设备ID
     * @return 构建结果
     */
    public Map<String, Object> buildFromImage(String imageId, MultipartFile imageFile, String equipmentId) {
        log.info("开始从图像构建知识图谱: imageId={}, equipmentId={}", imageId, equipmentId);
        
        try {
            // Step 1: 调用采集层解析图像
            log.info("Step 1: 调用采集层解析图像");
            List<Map<String, Object>> components = callImageParser(imageFile);
            log.info("图像解析完成，发现 {} 个部件", components.size());

            // Step 2: 创建设备节点
            log.info("Step 2: 创建设备节点");
            String equipmentName = "设备_" + equipmentId;
            Map<String, Object> equipmentNode = createEquipmentNode(equipmentId, equipmentName, 
                "从图像解析: " + imageId);
            
            // Step 3: 创建组件节点并关联
            log.info("Step 3: 创建组件节点");
            List<Map<String, Object>> savedComponents = new ArrayList<>();
            for (int i = 0; i < components.size(); i++) {
                Map<String, Object> comp = components.get(i);
                String componentId = equipmentId + "_comp_" + i;
                String componentName = (String) comp.getOrDefault("componentName", 
                    comp.getOrDefault("name", "部件_" + i));
                String componentType = (String) comp.getOrDefault("componentType", 
                    comp.getOrDefault("type", "unknown"));
                
                Map<String, Object> savedComp = createComponentNode(equipmentId, componentId, 
                    componentName, componentType, comp);
                savedComponents.add(savedComp);
            }

            // Step 4: 建立组件间关系（如果有位置信息）
            log.info("Step 4: 建立组件关系");
            int relationCount = createComponentRelations(components, equipmentId);

            Map<String, Object> result = new HashMap<>();
            result.put("equipmentId", equipmentId);
            result.put("equipmentNode", equipmentNode);
            result.put("componentCount", savedComponents.size());
            result.put("components", savedComponents);
            result.put("relationCount", relationCount);
            result.put("sourceImageId", imageId);
            
            log.info("知识图谱构建完成: 设备={}, 组件={}, 关系={}", 
                equipmentId, savedComponents.size(), relationCount);
            
            return result;

        } catch (Exception e) {
            log.error("从图像构建知识图谱失败: {}", e.getMessage(), e);
            throw new RuntimeException("构建失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从文本构建知识图谱
     * 
     * 流程：
     * 1. 调用采集层 /document/text 解析文本
     * 2. 提取实体和关系
     * 3. 保存到 Neo4j
     * 
     * @param docId 文档ID
     * @param text 文本内容
     * @param equipmentId 设备ID
     * @return 构建结果
     */
    public Map<String, Object> buildFromText(String docId, String text, String equipmentId) {
        log.info("开始从文本构建知识图谱: docId={}, equipmentId={}", docId, equipmentId);
        
        try {
            // Step 1: 调用采集层解析文本
            log.info("Step 1: 调用采集层解析文本");
            Map<String, Object> parseResult = callTextParser(text);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entities = (List<Map<String, Object>>) parseResult.getOrDefault("entities", new ArrayList<>());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> relations = (List<Map<String, Object>>) parseResult.getOrDefault("relations", new ArrayList<>());
            
            log.info("文本解析完成: {} 个实体, {} 个关系", entities.size(), relations.size());

            // Step 2: 创建设备节点（如果不存在）
            log.info("Step 2: 创建设备节点");
            Map<String, Object> equipmentNode = createEquipmentNode(equipmentId, 
                "设备_" + equipmentId, "从文本解析: " + docId);

            // Step 3: 创建实体节点
            log.info("Step 3: 创建实体节点");
            Map<String, String> entityIdMap = new HashMap<>();
            for (int i = 0; i < entities.size(); i++) {
                Map<String, Object> entity = entities.get(i);
                String entityName = (String) entity.getOrDefault("text", 
                    entity.getOrDefault("name", "实体_" + i));
                String entityType = (String) entity.getOrDefault("type", "component");
                String componentId = equipmentId + "_" + entityType + "_" + i;
                
                Map<String, Object> savedEntity = createComponentNode(equipmentId, componentId, 
                    entityName, entityType, entity);
                entityIdMap.put(entityName, componentId);
            }

            // Step 4: 创建关系
            log.info("Step 4: 创建关系");
            int relationCount = 0;
            for (Map<String, Object> relation : relations) {
                String fromEntity = (String) relation.get("subject");
                String toEntity = (String) relation.get("object");
                String relationType = (String) relation.getOrDefault("relationType", 
                    relation.getOrDefault("predicate", "RELATED_TO"));
                
                String fromId = entityIdMap.get(fromEntity);
                String toId = entityIdMap.get(toEntity);
                
                if (fromId != null && toId != null) {
                    createRelation(fromId, toId, relationType, relation);
                    relationCount++;
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("equipmentId", equipmentId);
            result.put("equipmentNode", equipmentNode);
            result.put("entityCount", entities.size());
            result.put("relationCount", relationCount);
            result.put("sourceDocId", docId);
            
            log.info("知识图谱构建完成: 设备={}, 实体={}, 关系={}", 
                equipmentId, entities.size(), relationCount);
            
            return result;

        } catch (Exception e) {
            log.error("从文本构建知识图谱失败: {}", e.getMessage(), e);
            throw new RuntimeException("构建失败: " + e.getMessage(), e);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 调用采集层解析图像
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> callImageParser(MultipartFile imageFile) throws Exception {
        String url = collectionServiceUrl + "/document/image";
        
        // 创建multipart请求
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new MultipartFileResource(imageFile));
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        
        ResponseEntity<Map> response = restTemplate.exchange(
            url, HttpMethod.POST, requestEntity, Map.class);
        
        if (response.getBody() == null) {
            return new ArrayList<>();
        }
        
        Map<String, Object> responseBody = response.getBody();
        if (!"success".equals(responseBody.get("status"))) {
            throw new RuntimeException("图像解析失败: " + responseBody.get("message"));
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
        return (List<Map<String, Object>>) data.getOrDefault("components", new ArrayList<>());
    }

    /**
     * 调用采集层解析文本
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callTextParser(String text) {
        String url = collectionServiceUrl + "/document/text";
        
        Map<String, String> request = new HashMap<>();
        request.put("text", text);
        request.put("type", "maintenance");
        
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
        
        if (response.getBody() == null) {
            return new HashMap<>();
        }
        
        Map<String, Object> responseBody = response.getBody();
        if (!"success".equals(responseBody.get("status"))) {
            throw new RuntimeException("文本解析失败: " + responseBody.get("message"));
        }
        
        // 先取 data，再从 data 中取 parseResult
        Map<String, Object> data = (Map<String, Object>) responseBody.getOrDefault("data", new HashMap<>());
        return (Map<String, Object>) data.getOrDefault("parseResult", data);
    }

    /**
     * 创建设备节点
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> createEquipmentNode(String equipmentId, String name, String description) {
        String url = storageServiceUrl + "/storage/graph/equipment";
        
        Map<String, Object> equipment = new HashMap<>();
        equipment.put("equipmentId", equipmentId);
        equipment.put("name", name);
        equipment.put("description", description);
        equipment.put("type", "PARSED");
        
        ResponseEntity<Map> response = restTemplate.postForEntity(url, equipment, Map.class);
        return response.getBody() != null ? response.getBody() : equipment;
    }

    /**
     * 创建组件节点
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> createComponentNode(String equipmentId, String componentId, 
            String name, String type, Map<String, Object> properties) {
        String url = storageServiceUrl + "/storage/graph/component?equipmentId=" + equipmentId;
        
        Map<String, Object> component = new HashMap<>();
        component.put("componentId", componentId);
        component.put("name", name);
        component.put("type", type);
        component.put("properties", properties);
        
        ResponseEntity<Map> response = restTemplate.postForEntity(url, component, Map.class);
        return response.getBody() != null ? response.getBody() : component;
    }

    /**
     * 创建组件间关系
     */
    private int createComponentRelations(List<Map<String, Object>> components, String equipmentId) {
        // 简化实现：相邻组件建立关系
        int count = 0;
        String url = storageServiceUrl + "/storage/graph/relation";
        
        for (int i = 0; i < components.size() - 1; i++) {
            String fromId = equipmentId + "_comp_" + i;
            String toId = equipmentId + "_comp_" + (i + 1);
            
            Map<String, Object> relationRequest = new HashMap<>();
            relationRequest.put("fromComponentId", fromId);
            relationRequest.put("toComponentId", toId);
            relationRequest.put("relationType", "COMPONENT_RELATED");
            
            try {
                restTemplate.postForEntity(url, relationRequest, Map.class);
                count++;
            } catch (Exception e) {
                log.warn("创建关系失败: {} -> {}", fromId, toId);
            }
        }
        
        return count;
    }

    /**
     * 创建关系
     */
    private void createRelation(String fromId, String toId, String relationType, Map<String, Object> properties) {
        String url = storageServiceUrl + "/storage/graph/relation";
        
        Map<String, Object> relationRequest = new HashMap<>();
        relationRequest.put("fromComponentId", fromId);
        relationRequest.put("toComponentId", toId);
        relationRequest.put("relationType", relationType);
        relationRequest.put("properties", properties);
        
        try {
            restTemplate.postForEntity(url, relationRequest, Map.class);
        } catch (Exception e) {
            log.warn("创建关系失败: {} -> {}", fromId, toId);
        }
    }

    /**
     * MultipartFile 包装类，用于 RestTemplate 传输
     */
    private static class MultipartFileResource extends ByteArrayResource {
        private final String filename;
        
        public MultipartFileResource(MultipartFile file) throws Exception {
            super(file.getBytes());
            this.filename = file.getOriginalFilename();
        }
        
        @Override
        public String getFilename() {
            return filename;
        }
    }
}
