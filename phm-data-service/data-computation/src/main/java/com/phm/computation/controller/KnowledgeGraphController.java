package com.phm.computation.controller;

import com.phm.computation.service.KnowledgeGraphBuilderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 知识图谱构建控制器
 * 
 * 职责：
 * - 接收图像/文本数据
 * - 调用 KnowledgeGraphBuilderService 构建知识图谱
 * - 返回构建结果
 * 
 * 这是计算层的核心功能，体现从原始数据到知识的转换
 */
@Slf4j
@RestController
@RequestMapping("/computation/knowledge")
@RequiredArgsConstructor
public class KnowledgeGraphController {

    private final KnowledgeGraphBuilderService knowledgeGraphBuilderService;

    /**
     * 从图像构建知识图谱
     * 
     * 流程：
     * 1. 接收图像文件
     * 2. 调用采集层解析图像（提取部件）
     * 3. 保存设备/组件到 Neo4j
     * 4. 返回构建结果
     * 
     * @param file 图像文件
     * @param imageId 图像ID
     * @param equipmentId 设备ID（可选，默认自动生成）
     * @return 构建结果
     */
    @PostMapping("/build/image")
    public ResponseEntity<Map<String, Object>> buildFromImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("imageId") String imageId,
            @RequestParam(value = "equipmentId", required = false) String equipmentId) {
        
        log.info("接收到图像知识图谱构建请求: imageId={}, filename={}", 
            imageId, file.getOriginalFilename());

        if (equipmentId == null || equipmentId.isEmpty()) {
            equipmentId = "EQ_" + System.currentTimeMillis();
        }

        try {
            Map<String, Object> result = knowledgeGraphBuilderService.buildFromImage(
                imageId, file, equipmentId);
            
            log.info("图像知识图谱构建成功: equipmentId={}, 组件数={}", 
                result.get("equipmentId"), result.get("componentCount"));
            
            return ResponseEntity.ok(buildSuccessResponse(result, 
                "从图像构建知识图谱成功，共提取 " + result.get("componentCount") + " 个部件"));

        } catch (Exception e) {
            log.error("图像知识图谱构建失败: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(buildErrorResponse("构建失败: " + e.getMessage()));
        }
    }

    /**
     * 从文本构建知识图谱
     * 
     * 流程：
     * 1. 接收文本内容
     * 2. 调用采集层解析文本（提取实体关系）
     * 3. 保存到 Neo4j
     * 4. 返回构建结果
     * 
     * @param request 包含 docId, text, equipmentId
     * @return 构建结果
     */
    @PostMapping("/build/text")
    public ResponseEntity<Map<String, Object>> buildFromText(@RequestBody Map<String, String> request) {
        String docId = request.get("docId");
        String text = request.get("text");
        String equipmentId = request.getOrDefault("equipmentId", "EQ_" + System.currentTimeMillis());
        
        log.info("接收到文本知识图谱构建请求: docId={}, textLength={}", 
            docId, text != null ? text.length() : 0);

        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(buildErrorResponse("文本内容不能为空"));
        }

        try {
            Map<String, Object> result = knowledgeGraphBuilderService.buildFromText(docId, text, equipmentId);
            
            log.info("文本知识图谱构建成功: equipmentId={}, 实体数={}, 关系数={}", 
                result.get("equipmentId"), result.get("entityCount"), result.get("relationCount"));
            
            return ResponseEntity.ok(buildSuccessResponse(result, 
                "从文本构建知识图谱成功，共提取 " + result.get("entityCount") + " 个实体，" 
                + result.get("relationCount") + " 个关系"));

        } catch (Exception e) {
            log.error("文本知识图谱构建失败: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(buildErrorResponse("构建失败: " + e.getMessage()));
        }
    }

    // ==================== 私有方法 ====================

    private Map<String, Object> buildSuccessResponse(Object data, String note) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", data);
        response.put("note", note);
        return response;
    }

    private Map<String, Object> buildErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        return response;
    }
}
