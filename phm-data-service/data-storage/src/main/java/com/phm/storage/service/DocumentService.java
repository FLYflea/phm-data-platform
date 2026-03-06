package com.phm.storage.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * P2: 文档存储服务
 *
 * 功能：
 * - 文档保存（内存存储，实际应接入Elasticsearch或MongoDB）
 * - 文档搜索（简单关键词匹配，实际应使用全文检索）
 *
 * TODO: P2待实现完整版
 * - 接入Elasticsearch实现全文检索
 * - 接入MongoDB存储文档内容
 * - 支持文档分类、标签、权限管理
 */
@Slf4j
@Service
public class DocumentService {

    /**
     * 内存文档存储（模拟数据库）
     * TODO: P2待实现 - 替换为Elasticsearch或MongoDB
     */
    private final Map<String, Document> documentStore = new ConcurrentHashMap<>();

    /**
     * P2: 保存文档
     *
     * @param docId 文档ID
     * @param title 标题
     * @param content 内容
     * @param type 类型
     * @param metadata 元数据
     * @return 保存的文档
     */
    public Document saveDocument(String docId, String title, String content, 
                                  String type, Map<String, Object> metadata) {
        if (docId == null || docId.isEmpty()) {
            log.warn("保存文档失败：docId为空");
            return null;
        }

        log.info("P2保存文档: docId={}, title={}, type={}", docId, title, type);

        Document doc = new Document();
        doc.setDocId(docId);
        doc.setTitle(title);
        doc.setContent(content);
        doc.setType(type);
        doc.setMetadata(metadata);
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());

        documentStore.put(docId, doc);

        log.info("P2文档保存成功: docId={}", docId);
        return doc;
    }

    /**
     * P2: 根据关键词搜索文档
     *
     * TODO: P2待实现 - 使用Elasticsearch全文检索替代简单字符串匹配
     *
     * @param keyword 关键词
     * @return 匹配的文档列表
     */
    public List<Document> searchDocuments(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return new ArrayList<>(documentStore.values());
        }

        log.info("P2搜索文档: keyword={}", keyword);

        String lowerKeyword = keyword.toLowerCase();
        List<Document> results = documentStore.values().stream()
                .filter(doc -> matchesKeyword(doc, lowerKeyword))
                .collect(Collectors.toList());

        log.info("P2搜索完成: 找到 {} 个匹配文档", results.size());
        return results;
    }

    /**
     * P2: 根据ID获取文档
     *
     * @param docId 文档ID
     * @return 文档（Optional）
     */
    public Optional<Document> getDocument(String docId) {
        return Optional.ofNullable(documentStore.get(docId));
    }

    /**
     * P2: 删除文档
     *
     * @param docId 文档ID
     * @return 是否删除成功
     */
    public boolean deleteDocument(String docId) {
        if (docId == null || !documentStore.containsKey(docId)) {
            return false;
        }
        documentStore.remove(docId);
        log.info("P2文档删除成功: docId={}", docId);
        return true;
    }

    /**
     * P2: 获取所有文档
     *
     * @return 文档列表
     */
    public List<Document> getAllDocuments() {
        return new ArrayList<>(documentStore.values());
    }

    /**
     * P2: 获取文档数量
     *
     * @return 文档数量
     */
    public long getDocumentCount() {
        return documentStore.size();
    }

    /**
     * 关键词匹配（简单实现）
     */
    private boolean matchesKeyword(Document doc, String keyword) {
        return (doc.getTitle() != null && doc.getTitle().toLowerCase().contains(keyword)) ||
               (doc.getContent() != null && doc.getContent().toLowerCase().contains(keyword)) ||
               (doc.getType() != null && doc.getType().toLowerCase().contains(keyword));
    }

    /**
     * P2: 文档实体
     */
    @lombok.Data
    public static class Document {
        private String docId;
        private String title;
        private String content;
        private String type;
        private Map<String, Object> metadata;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
