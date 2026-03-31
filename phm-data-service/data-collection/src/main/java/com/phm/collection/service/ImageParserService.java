package com.phm.collection.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 设计图纸解析服务
 * 
 * 功能：
 * - 解析设备设计图纸，识别部件信息
 * - 提取部件之间的关联关系
 * 
 * TODO: YOLOv8 模型待接入
 * - 当前返回模拟数据，实际应调用 YOLOv8 目标检测模型
 * - 需集成 OpenCV 进行图像预处理
 * - 需训练专用数据集以识别工业设备部件
 */
@Slf4j
@Service
public class ImageParserService {

    /**
     * 缓存已解析的部件信息，用于后续提取关联关系
     * Key: imageId, Value: 部件列表
     */
    private final Map<String, List<Map<String, Object>>> parsedComponentsCache = new ConcurrentHashMap<>();

    /**
     * 部件定义：关键词 -> 部件配置列表
     * 每个部件配置包含：名称、类型、注释模板
     */
    private static final Map<String, List<ComponentTemplate>> KEYWORD_COMPONENT_MAP = new LinkedHashMap<>();
    
    /**
     * 通用设备部件列表（当文件名无法识别时使用）
     */
    private static final List<ComponentTemplate> DEFAULT_COMPONENTS = Arrays.asList(
        new ComponentTemplate("电机", "MOTOR", "动力源，监测电流和温度"),
        new ComponentTemplate("联轴器", "COUPLING", "连接部件，注意对中状态"),
        new ComponentTemplate("主轴", "SHAFT", "旋转轴，监测振动"),
        new ComponentTemplate("底座", "BASE", "支撑结构，检查固定螺栓")
    );

    static {
        // 轴承相关关键词
        KEYWORD_COMPONENT_MAP.put("bearing|轴承", Arrays.asList(
            new ComponentTemplate("深沟球轴承", "BEARING_BALL", "滚动轴承，监测温度和振动"),
            new ComponentTemplate("滚子轴承", "BEARING_ROLLER", "承载径向载荷，注意润滑"),
            new ComponentTemplate("轴承座", "BEARING_HOUSING", "轴承安装座，检查密封"),
            new ComponentTemplate("轴承端盖", "BEARING_CAP", "轴承端部密封件")
        ));
        
        // 齿轮相关关键词
        KEYWORD_COMPONENT_MAP.put("gear|gearbox|齿轮|变速", Arrays.asList(
            new ComponentTemplate("主动齿轮", "GEAR_DRIVE", "传动主动件，监测齿面磨损"),
            new ComponentTemplate("从动齿轮", "GEAR_DRIVEN", "传动从动件，注意啮合状态"),
            new ComponentTemplate("齿轮轴", "GEAR_SHAFT", "承载齿轮的旋转轴"),
            new ComponentTemplate("齿轮箱壳体", "GEARBOX_HOUSING", "齿轮箱外壳，检查油位"),
            new ComponentTemplate("输入轴承", "INPUT_BEARING", "输入端支撑轴承"),
            new ComponentTemplate("输出轴承", "OUTPUT_BEARING", "输出端支撑轴承")
        ));
        
        // 电机相关关键词
        KEYWORD_COMPONENT_MAP.put("motor|电机|驱动", Arrays.asList(
            new ComponentTemplate("电机定子", "MOTOR_STATOR", "电机固定绑组，监测温度"),
            new ComponentTemplate("电机转子", "MOTOR_ROTOR", "电机旋转部件，监测振动"),
            new ComponentTemplate("电机轴承", "MOTOR_BEARING", "电机内部轴承"),
            new ComponentTemplate("电机端盖", "MOTOR_ENDCAP", "电机端部结构件"),
            new ComponentTemplate("冷却风扇", "COOLING_FAN", "电机散热风扇")
        ));
        
        // 泵相关关键词
        KEYWORD_COMPONENT_MAP.put("pump|泵|液压", Arrays.asList(
            new ComponentTemplate("泵体", "PUMP_BODY", "泵主体结构，检查密封"),
            new ComponentTemplate("叶轮", "IMPELLER", "旋转叶轮，监测流量"),
            new ComponentTemplate("泵轴", "PUMP_SHAFT", "泵旋转轴"),
            new ComponentTemplate("机械密封", "MECHANICAL_SEAL", "轴端密封件，防止泄漏"),
            new ComponentTemplate("泵轴承", "PUMP_BEARING", "泵轴支撑轴承")
        ));
        
        // 压缩机相关关键词
        KEYWORD_COMPONENT_MAP.put("compressor|压缩机|空压", Arrays.asList(
            new ComponentTemplate("压缩机缸体", "COMPRESSOR_CYLINDER", "压缩工作腔体"),
            new ComponentTemplate("活塞", "PISTON", "往复运动部件"),
            new ComponentTemplate("连杆", "CONNECTING_ROD", "连接曲轴与活塞"),
            new ComponentTemplate("曲轴", "CRANKSHAFT", "旋转运动转换部件"),
            new ComponentTemplate("阀片", "VALVE_PLATE", "进排气控制阀")
        ));
        
        // 风机相关关键词
        KEYWORD_COMPONENT_MAP.put("fan|blower|风机|鼓风", Arrays.asList(
            new ComponentTemplate("风机叶轮", "FAN_IMPELLER", "旋转叶片，产生气流"),
            new ComponentTemplate("风机蜗壳", "FAN_VOLUTE", "气流导向壳体"),
            new ComponentTemplate("风机轴", "FAN_SHAFT", "风机旋转轴"),
            new ComponentTemplate("风机轴承", "FAN_BEARING", "风机支撑轴承")
        ));
    }

    /**
     * 解析设计图纸，识别部件列表
     * 
     * 基于规则的模拟解析实现：
     * - 读取图片实际属性（文件名、大小、尺寸）
     * - 根据文件名关键词动态生成部件识别结果
     * - 模拟真实YOLO模型的处理延迟和输出格式
     * 
     * @param file 设计图纸文件（支持 jpg, png, pdf 等格式）
     * @return 部件列表，每个部件包含 componentId, componentName, confidence, boundingBox, componentType
     */
    public List<Map<String, Object>> parseDesignImage(MultipartFile file) {
        long startTime = System.currentTimeMillis();
        
        if (file == null || file.isEmpty()) {
            log.warn("上传的文件为空");
            return Collections.emptyList();
        }

        String filename = file.getOriginalFilename();
        long fileSize = file.getSize();
        log.info("开始解析设计图纸: {}, 大小: {} 字节", filename, fileSize);

        // 尝试读取图片尺寸
        int imageWidth = 1920;  // 默认尺寸
        int imageHeight = 1080;
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image != null) {
                imageWidth = image.getWidth();
                imageHeight = image.getHeight();
                log.info("图片尺寸: {}x{}", imageWidth, imageHeight);
            }
        } catch (IOException e) {
            log.warn("无法读取图片尺寸，使用默认值: {}", e.getMessage());
        }

        // 模拟处理延迟（根据文件大小动态调整，100KB-1MB对应50-200ms）
        simulateProcessingDelay(fileSize);

        // 根据文件名关键词匹配部件模板
        List<ComponentTemplate> matchedTemplates = matchComponentsByFilename(filename);
        
        // 生成部件识别结果
        List<Map<String, Object>> components = generateComponents(matchedTemplates, imageWidth, imageHeight);
        
        // 生成图像ID并缓存解析结果
        String imageId = generateImageId(filename);
        parsedComponentsCache.put(imageId, components);
        
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("图纸解析完成: {}, 识别到 {} 个部件, 耗时: {}ms, imageId: {}", 
                 filename, components.size(), elapsed, imageId);
        
        // 在结果中附带imageId，方便后续提取关系
        for (Map<String, Object> comp : components) {
            comp.put("imageId", imageId);
        }
        
        return components;
    }
    
    /**
     * 根据文件名匹配部件模板
     */
    private List<ComponentTemplate> matchComponentsByFilename(String filename) {
        if (filename == null) {
            return new ArrayList<>(DEFAULT_COMPONENTS);
        }
        
        String lowerFilename = filename.toLowerCase();
        
        for (Map.Entry<String, List<ComponentTemplate>> entry : KEYWORD_COMPONENT_MAP.entrySet()) {
            String[] keywords = entry.getKey().split("\\|");
            for (String keyword : keywords) {
                if (lowerFilename.contains(keyword.toLowerCase())) {
                    log.info("文件名匹配关键词: '{}', 使用对应部件模板", keyword);
                    return new ArrayList<>(entry.getValue());
                }
            }
        }
        
        log.info("文件名无法识别特定设备类型，使用通用部件列表");
        return new ArrayList<>(DEFAULT_COMPONENTS);
    }
    
    /**
     * 生成部件识别结果
     */
    private List<Map<String, Object>> generateComponents(List<ComponentTemplate> templates, 
                                                          int imageWidth, int imageHeight) {
        List<Map<String, Object>> components = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        
        int count = templates.size();
        int gridCols = (int) Math.ceil(Math.sqrt(count));
        int gridRows = (int) Math.ceil((double) count / gridCols);
        int cellWidth = imageWidth / gridCols;
        int cellHeight = imageHeight / gridRows;
        
        for (int i = 0; i < templates.size(); i++) {
            ComponentTemplate template = templates.get(i);
            
            Map<String, Object> component = new HashMap<>();
            component.put("componentId", UUID.randomUUID().toString());
            component.put("componentName", template.name);
            component.put("componentType", template.type);
            component.put("confidence", roundToTwoDecimals(random.nextDouble(0.75, 0.98)));
            component.put("note", template.note);
            
            // 生成模拟边界框（基于网格布局 + 随机偏移）
            int col = i % gridCols;
            int row = i / gridCols;
            int boxWidth = (int) (cellWidth * random.nextDouble(0.4, 0.7));
            int boxHeight = (int) (cellHeight * random.nextDouble(0.4, 0.7));
            int x = col * cellWidth + random.nextInt(cellWidth - boxWidth);
            int y = row * cellHeight + random.nextInt(Math.max(1, cellHeight - boxHeight));
            
            Map<String, Integer> boundingBox = new LinkedHashMap<>();
            boundingBox.put("x", x);
            boundingBox.put("y", y);
            boundingBox.put("width", boxWidth);
            boundingBox.put("height", boxHeight);
            component.put("boundingBox", boundingBox);
            
            components.add(component);
        }
        
        return components;
    }
    
    /**
     * 模拟处理延迟
     */
    private void simulateProcessingDelay(long fileSize) {
        // 根据文件大小计算延迟：50ms基础 + 每100KB增加15ms，最大200ms
        long delay = Math.min(200, 50 + (fileSize / 100_000) * 15);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 生成图像ID
     */
    private String generateImageId(String filename) {
        String baseName = filename != null ? filename.replaceAll("[^a-zA-Z0-9]", "_") : "unknown";
        return "IMG_" + baseName + "_" + System.currentTimeMillis();
    }
    
    /**
     * 保留两位小数
     */
    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * 提取部件之间的关联关系
     * 
     * 基于规则的关系推断实现：
     * - 根据已识别的部件动态生成关联关系
     * - 基于部件类型推断合理的连接关系
     * - 生成双向关系
     * 
     * @param imageId 图纸/图像ID
     * @return 部件关联关系列表
     */
    public List<Map<String, Object>> extractComponentRelations(String imageId) {
        if (imageId == null || imageId.isEmpty()) {
            log.warn("图像ID为空");
            return Collections.emptyList();
        }

        log.info("开始提取部件关联关系, imageId: {}", imageId);

        // 从缓存获取已解析的部件
        List<Map<String, Object>> components = parsedComponentsCache.get(imageId);
        if (components == null || components.isEmpty()) {
            log.warn("未找到imageId对应的部件数据: {}, 尝试模糊匹配", imageId);
            // 尝试模糊匹配（匹配包含相同前缀的imageId）
            components = findComponentsByPartialImageId(imageId);
            if (components == null || components.isEmpty()) {
                log.warn("无法找到任何匹配的部件数据");
                return Collections.emptyList();
            }
        }

        List<Map<String, Object>> relations = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 基于部件类型生成关联关系
        for (int i = 0; i < components.size(); i++) {
            Map<String, Object> sourceComp = components.get(i);
            String sourceId = (String) sourceComp.get("componentId");
            String sourceName = (String) sourceComp.get("componentName");
            String sourceType = (String) sourceComp.get("componentType");

            for (int j = i + 1; j < components.size(); j++) {
                Map<String, Object> targetComp = components.get(j);
                String targetId = (String) targetComp.get("componentId");
                String targetName = (String) targetComp.get("componentName");
                String targetType = (String) targetComp.get("componentType");

                // 根据部件类型推断关系
                RelationInfo relationInfo = inferRelation(sourceType, targetType, sourceName, targetName);
                if (relationInfo != null) {
                    // 正向关系
                    Map<String, Object> relation = new LinkedHashMap<>();
                    relation.put("sourceId", sourceId);
                    relation.put("sourceName", sourceName);
                    relation.put("targetId", targetId);
                    relation.put("targetName", targetName);
                    relation.put("relationType", relationInfo.type);
                    relation.put("relationName", relationInfo.name);
                    relation.put("confidence", roundToTwoDecimals(random.nextDouble(0.78, 0.95)));
                    relation.put("note", relationInfo.note);
                    relations.add(relation);

                    // 反向关系
                    Map<String, Object> reverseRelation = new LinkedHashMap<>();
                    reverseRelation.put("sourceId", targetId);
                    reverseRelation.put("sourceName", targetName);
                    reverseRelation.put("targetId", sourceId);
                    reverseRelation.put("targetName", sourceName);
                    reverseRelation.put("relationType", relationInfo.reverseType);
                    reverseRelation.put("relationName", relationInfo.reverseName);
                    reverseRelation.put("confidence", roundToTwoDecimals(random.nextDouble(0.78, 0.95)));
                    reverseRelation.put("note", relationInfo.reverseNote);
                    relations.add(reverseRelation);
                }
            }
        }

        log.info("关联关系提取完成, imageId: {}, 共 {} 条关系", imageId, relations.size());
        return relations;
    }
    
    /**
     * 模糊匹配imageId
     */
    private List<Map<String, Object>> findComponentsByPartialImageId(String partialId) {
        for (Map.Entry<String, List<Map<String, Object>>> entry : parsedComponentsCache.entrySet()) {
            if (entry.getKey().contains(partialId) || partialId.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        // 返回最近一次解析的结果
        if (!parsedComponentsCache.isEmpty()) {
            return parsedComponentsCache.values().iterator().next();
        }
        return null;
    }
    
    /**
     * 根据部件类型推断关系
     */
    private RelationInfo inferRelation(String sourceType, String targetType, 
                                        String sourceName, String targetName) {
        if (sourceType == null || targetType == null) {
            return null;
        }
        
        // MOTOR 驱动其他旋转部件
        if (sourceType.contains("MOTOR") && !targetType.contains("MOTOR")) {
            return new RelationInfo(
                "DRIVES", "驱动", sourceName + "为" + targetName + "提供动力",
                "DRIVEN_BY", "被驱动", targetName + "由" + sourceName + "驱动"
            );
        }
        
        // COUPLING 连接部件
        if (sourceType.contains("COUPLING")) {
            return new RelationInfo(
                "CONNECTS", "连接", sourceName + "连接" + targetName,
                "CONNECTED_TO", "被连接", targetName + "通过" + sourceName + "连接"
            );
        }
        
        // BEARING 支撑轴类部件
        if (sourceType.contains("BEARING") && targetType.contains("SHAFT")) {
            return new RelationInfo(
                "SUPPORTS", "支撑", sourceName + "支撑" + targetName + "旋转",
                "SUPPORTED_BY", "被支撑", targetName + "由" + sourceName + "支撑"
            );
        }
        
        // SHAFT 被 BEARING 支撑
        if (sourceType.contains("SHAFT") && targetType.contains("BEARING")) {
            return new RelationInfo(
                "MOUNTED_ON", "安装于", sourceName + "安装于" + targetName,
                "MOUNTS", "承载", targetName + "承载" + sourceName
            );
        }
        
        // HOUSING/壳体 包含内部部件
        if (sourceType.contains("HOUSING") || sourceType.contains("BODY")) {
            return new RelationInfo(
                "CONTAINS", "包含", sourceName + "内部包含" + targetName,
                "CONTAINED_IN", "位于", targetName + "位于" + sourceName + "内部"
            );
        }
        
        // GEAR 齿轮之间的啮合关系
        if (sourceType.contains("GEAR") && targetType.contains("GEAR")) {
            return new RelationInfo(
                "MESHES_WITH", "啮合", sourceName + "与" + targetName + "啮合传动",
                "MESHES_WITH", "啮合", targetName + "与" + sourceName + "啮合传动"
            );
        }
        
        // BASE 支撑上层部件
        if (sourceType.contains("BASE")) {
            return new RelationInfo(
                "SUPPORTS", "支撑", sourceName + "支撑" + targetName,
                "MOUNTED_ON", "安装于", targetName + "安装于" + sourceName
            );
        }
        
        // 默认：相邻部件建立连接关系（基于边界框距离可以进一步优化）
        return new RelationInfo(
            "CONNECTS", "连接", sourceName + "与" + targetName + "相连",
            "CONNECTS", "连接", targetName + "与" + sourceName + "相连"
        );
    }

    /**
     * 获取支持的文件类型
     * 
     * @return 支持的文件扩展名列表
     */
    public List<String> getSupportedFormats() {
        return Arrays.asList("jpg", "jpeg", "png", "bmp", "pdf", "dwg");
    }

    /**
     * 验证文件格式是否支持
     * 
     * @param filename 文件名
     * @return 是否支持
     */
    public boolean isSupportedFormat(String filename) {
        if (filename == null || !filename.contains(".")) {
            return false;
        }
        String ext = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return getSupportedFormats().contains(ext);
    }
    
    /**
     * 获取缓存的部件数据（用于测试或调试）
     */
    public List<Map<String, Object>> getCachedComponents(String imageId) {
        return parsedComponentsCache.get(imageId);
    }
    
    /**
     * 清除缓存
     */
    public void clearCache() {
        parsedComponentsCache.clear();
        log.info("部件缓存已清除");
    }
    
    // ==================== 内部辅助类 ====================
    
    /**
     * 部件模板定义
     */
    private static class ComponentTemplate {
        final String name;      // 部件名称
        final String type;      // 部件类型编码
        final String note;      // 注释说明
        
        ComponentTemplate(String name, String type, String note) {
            this.name = name;
            this.type = type;
            this.note = note;
        }
    }
    
    /**
     * 关系信息定义
     */
    private static class RelationInfo {
        final String type;          // 正向关系类型
        final String name;          // 正向关系名称
        final String note;          // 正向关系说明
        final String reverseType;   // 反向关系类型
        final String reverseName;   // 反向关系名称
        final String reverseNote;   // 反向关系说明
        
        RelationInfo(String type, String name, String note,
                     String reverseType, String reverseName, String reverseNote) {
            this.type = type;
            this.name = name;
            this.note = note;
            this.reverseType = reverseType;
            this.reverseName = reverseName;
            this.reverseNote = reverseNote;
        }
    }
}
