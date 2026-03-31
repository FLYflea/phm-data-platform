package com.phm.collection.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本解析服务
 * 
 * 采用"正则表达式 + 实体关系规则模板匹配"混合技术实现：
 * - 正则表达式：提取时间、数值、人员等结构化实体
 * - 词典匹配：识别设备部件、故障模式、维修动作
 * - 规则模板：提取实体间关系三元组
 * 
 * 功能：
 * - 解析维修记录文本，提取故障模式、维修措施和实体关系
 * - 解析 FMECA 知识文本，提取失效模式信息
 */
@Slf4j
@Service
public class TextParserService {

    // ==================== 实体词典 ====================
    
    // 故障模式词典（支持复合描述）
    private static final List<String> FAULT_MODE_DICT = Arrays.asList(
            // 基础故障模式
            "磨损", "断裂", "裂纹", "腐蚀", "变形", "松动", "卡死", "过热",
            "异响", "振动", "泄漏", "堵塞", "短路", "断路", "老化", "失效",
            "烧蚀", "剥落", "点蚀", "胶合", "疲劳", "脱落", "开裂", "熔断",
            // 复合故障描述
            "温度过高", "温度异常", "振动异常", "振动过大", "磨损严重",
            "噪音异常", "压力过高", "压力不足", "转速异常", "电流过大",
            "绝缘老化", "密封失效", "润滑不良", "供油不足", "散热不良"
    );

    // 维修动作词典
    private static final List<String> MAINTENANCE_ACTION_DICT = Arrays.asList(
            "更换", "修复", "清洗", "润滑", "调整", "紧固", "校准", "检测",
            "保养", "维修", "替换", "补焊", "打磨", "抛光", "涂覆", "除锈",
            "拆卸", "安装", "检查", "测量", "校正", "加注", "排放", "冲洗"
    );

    // 设备部件词典（支持复合词）
    private static final List<String> COMPONENT_DICT = Arrays.asList(
            // 轴承类
            "轴承", "主轴承", "深沟球轴承", "滚子轴承", "推力轴承", "轴承座",
            // 齿轮类  
            "齿轮", "主动齿轮", "从动齿轮", "一级齿轮", "二级齿轮", "齿轮箱",
            // 泵类
            "泵", "泵体", "润滑油泵", "冷却水泵", "液压泵", "叶轮",
            // 电机类
            "电机", "电动机", "驱动电机", "伺服电机", "定子", "转子",
            // 传动类
            "联轴器", "皮带", "链条", "传动轴", "主轴", "输入轴", "输出轴",
            // 密封类
            "密封圈", "机械密封", "油封", "O型圈",
            // 其他
            "阀门", "传感器", "控制器", "开关", "接触器", "滤芯", "冷却器"
    );

    // ==================== 正则表达式模式 ====================
    
    // 时间模式：支持多种格式
    private static final Pattern[] TIME_PATTERNS = {
        // 2024年3月5日 / 2024年03月05日
        Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日"),
        // 2024-03-05 10:30 / 2024-03-05 10:30:00
        Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})(?:\\s+(\\d{2}):(\\d{2})(?::(\\d{2}))?)?"),
        // 2024/03/05
        Pattern.compile("(\\d{4})/(\\d{2})/(\\d{2})"),
        // 3月5日上午/下午/晚上
        Pattern.compile("(\\d{1,2})月(\\d{1,2})日(?:上午|下午|晚上|凌晨)?"),
        // 上午10点30分 / 下午3点
        Pattern.compile("(?:上午|下午|晚上|凌晨)?(\\d{1,2})(?:点|时)(\\d{1,2})?(?:分)?")
    };
    
    // 数值+单位模式
    private static final Pattern VALUE_UNIT_PATTERN = Pattern.compile(
        "(\\d+(?:\\.\\d+)?)\\s*" +
        "(°C|℃|度|rpm|RPM|转|r/min|Hz|赫兹|" +
        "mm|毫米|cm|厘米|m|米|km|" +
        "kg|千克|g|克|t|吨|" +
        "MPa|kPa|Pa|bar|psi|" +
        "A|mA|V|kV|W|kW|MW|" +
        "L|升|mL|毫升|" +
        "小时|分钟|秒|天|h|min|s)"
    );
    
    // 人员模式：技术员张伟、操作人员李明、工程师王强
    private static final Pattern PERSON_PATTERN = Pattern.compile(
        "(?:技术员|操作人员|维修人员|工程师|负责人|操作员|检修员|班长)" +
        "[：:]?\\s*([\\u4e00-\\u9fa5]{2,4})"
    );
    
    // 维修动作动宾结构：更换了XX、对XX进行了清洗、拆卸XX并修复
    private static final Pattern[] ACTION_PATTERNS = {
        // 更换了主轴承
        Pattern.compile("(更换|替换|安装)了?([\\u4e00-\\u9fa5]{2,8})"),
        // 对主轴承进行了清洗
        Pattern.compile("对([\\u4e00-\\u9fa5]{2,8})进行了?(更换|清洗|检查|维修|润滑|调整|校准)"),
        // 拆卸齿轮并修复
        Pattern.compile("(拆卸|检查)([\\u4e00-\\u9fa5]{2,8})(?:并|后)(修复|更换|清洗|调整)"),
        // 完成XX的更换/检修
        Pattern.compile("完成(?:了)?([\\u4e00-\\u9fa5]{2,8})的(更换|检修|维修|保养)")
    };
    
    // ==================== 关系规则模板 ====================
    
    // 故障-原因模板
    private static final Pattern[] FAULT_CAUSE_PATTERNS = {
        // XX导致XX
        Pattern.compile("([\\u4e00-\\u9fa5]{2,10})导致([\\u4e00-\\u9fa5]{2,10})"),
        // 因XX造成XX
        Pattern.compile("因([\\u4e00-\\u9fa5]{2,10})造成([\\u4e00-\\u9fa5]{2,10})"),
        // 由于XX引起XX
        Pattern.compile("由于([\\u4e00-\\u9fa5]{2,10})引起([\\u4e00-\\u9fa5]{2,10})"),
        // XX引发XX
        Pattern.compile("([\\u4e00-\\u9fa5]{2,10})引发([\\u4e00-\\u9fa5]{2,10})"),
        // XX使得XX
        Pattern.compile("([\\u4e00-\\u9fa5]{2,10})使得?([\\u4e00-\\u9fa5]{2,10})")
    };
    
    // 维修-结果模板
    private static final Pattern[] REPAIR_RESULT_PATTERNS = {
        // 更换XX后恢复正常
        Pattern.compile("(更换|维修|清洗|调整)([\\u4e00-\\u9fa5]{2,8})后[，,]?(?:设备|系统)?恢复正常"),
        // 经XX处理，XX正常
        Pattern.compile("经([\\u4e00-\\u9fa5]{2,8})处理[，,]([\\u4e00-\\u9fa5]{2,8})正常"),
        // 处理后XX恢复
        Pattern.compile("处理后[，,]?([\\u4e00-\\u9fa5]{2,8})恢复"),
        // XX修复完成
        Pattern.compile("([\\u4e00-\\u9fa5]{2,8})(修复|更换|维修)完成")
    };

    /**
     * 解析维修记录文本，提取故障模式、维修措施和实体关系
     * 
     * 采用"正则表达式 + 实体关系规则模板匹配"混合技术：
     * - 正则表达式提取：时间、数值+单位、人员等结构化实体
     * - 词典匹配：设备部件、故障模式、维修动作
     * - 规则模板：提取实体间关系三元组
     * 
     * @param text 维修记录文本
     * @return 提取结果，包含 entities, relations, faultPattern, maintenanceAction 等
     */
    public Map<String, Object> parseMaintenanceText(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.warn("维修记录文本为空");
            return Collections.emptyMap();
        }

        log.info("开始解析维修记录文本，长度: {} 字符", text.length());
        long startTime = System.currentTimeMillis();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("originalText", text);

        // ========== 1. 实体提取 ==========
        List<Map<String, Object>> entities = new ArrayList<>();
        
        // 1.1 提取时间实体
        extractTimeEntities(text, entities);
        
        // 1.2 提取数值+单位实体
        extractValueUnitEntities(text, entities);
        
        // 1.3 提取人员实体
        extractPersonEntities(text, entities);
        
        // 1.4 提取设备部件实体（词典+正则）
        List<Map<String, Object>> componentEntities = extractDictEntities(text, COMPONENT_DICT, "COMPONENT");
        entities.addAll(componentEntities);
        
        // 1.5 提取故障模式实体
        List<Map<String, Object>> faultEntities = extractDictEntities(text, FAULT_MODE_DICT, "FAULT_MODE");
        entities.addAll(faultEntities);
        
        // 1.6 提取维修动作实体
        List<Map<String, Object>> actionEntities = extractDictEntities(text, MAINTENANCE_ACTION_DICT, "MAINTENANCE_ACTION");
        entities.addAll(actionEntities);
        
        // 1.7 提取动宾结构维修动作
        extractActionPhraseEntities(text, entities);
        
        // 实体按位置排序并去重
        entities = deduplicateAndSortEntities(entities);
        result.put("entities", entities);

        // ========== 2. 关系提取 ==========
        List<Map<String, Object>> relations = new ArrayList<>();
        
        // 2.1 提取故障-原因关系
        extractFaultCauseRelations(text, relations);
        
        // 2.2 提取维修-结果关系
        extractRepairResultRelations(text, relations);
        
        // 2.3 基于实体推断关系三元组
        inferRelationsFromEntities(entities, relations);
        
        result.put("relations", relations);

        // ========== 3. 兼容原有字段 ==========
        List<String> detectedFaults = extractEntityTexts(faultEntities);
        List<String> detectedActions = extractEntityTexts(actionEntities);
        List<String> detectedComponents = extractEntityTexts(componentEntities);
        
        result.put("faultPatterns", detectedFaults);
        result.put("faultPattern", detectedFaults.isEmpty() ? "未识别" : detectedFaults.get(0));
        result.put("maintenanceActions", detectedActions);
        result.put("maintenanceAction", detectedActions.isEmpty() ? "未识别" : detectedActions.get(0));
        result.put("components", detectedComponents);
        result.put("component", detectedComponents.isEmpty() ? "未识别" : detectedComponents.get(0));
        
        // 时间和人员
        result.put("maintenanceTime", extractFirstTimeString(entities));
        result.put("maintenancePerson", extractFirstPersonString(entities));
        
        // 置信度和备注
        result.put("confidence", calculateConfidence(detectedFaults, detectedActions, detectedComponents));
        result.put("note", "基于正则表达式+规则模板匹配提取");
        
        long elapsed = System.currentTimeMillis() - startTime;
        result.put("parseTimeMs", elapsed);

        log.info("维修记录解析完成，识别实体: {}个, 关系: {}个, 耗时: {}ms", 
                entities.size(), relations.size(), elapsed);
        return result;
    }
    
    // ==================== 实体提取方法 ====================
    
    /**
     * 提取时间实体
     */
    private void extractTimeEntities(String text, List<Map<String, Object>> entities) {
        for (Pattern pattern : TIME_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                Map<String, Object> entity = new LinkedHashMap<>();
                entity.put("text", matcher.group());
                entity.put("type", "TIME");
                entity.put("startPos", matcher.start());
                entity.put("endPos", matcher.end());
                entities.add(entity);
            }
        }
    }
    
    /**
     * 提取数值+单位实体
     */
    private void extractValueUnitEntities(String text, List<Map<String, Object>> entities) {
        Matcher matcher = VALUE_UNIT_PATTERN.matcher(text);
        while (matcher.find()) {
            Map<String, Object> entity = new LinkedHashMap<>();
            entity.put("text", matcher.group());
            entity.put("type", "VALUE_UNIT");
            entity.put("value", matcher.group(1));
            entity.put("unit", matcher.group(2));
            entity.put("startPos", matcher.start());
            entity.put("endPos", matcher.end());
            entities.add(entity);
        }
    }
    
    /**
     * 提取人员实体
     */
    private void extractPersonEntities(String text, List<Map<String, Object>> entities) {
        Matcher matcher = PERSON_PATTERN.matcher(text);
        while (matcher.find()) {
            Map<String, Object> entity = new LinkedHashMap<>();
            entity.put("text", matcher.group());
            entity.put("type", "PERSON");
            entity.put("name", matcher.group(1));
            entity.put("startPos", matcher.start());
            entity.put("endPos", matcher.end());
            entities.add(entity);
        }
    }
    
    /**
     * 基于词典提取实体（支持复合词优先匹配）
     */
    private List<Map<String, Object>> extractDictEntities(String text, List<String> dict, String type) {
        List<Map<String, Object>> entities = new ArrayList<>();
        
        // 按长度降序排序，优先匹配长词
        List<String> sortedDict = new ArrayList<>(dict);
        sortedDict.sort((a, b) -> b.length() - a.length());
        
        // 记录已匹配位置，避免重复
        Set<Integer> matchedPositions = new HashSet<>();
        
        for (String word : sortedDict) {
            int index = 0;
            while ((index = text.indexOf(word, index)) != -1) {
                // 检查是否已被更长的词匹配
                boolean overlapped = false;
                for (int i = index; i < index + word.length(); i++) {
                    if (matchedPositions.contains(i)) {
                        overlapped = true;
                        break;
                    }
                }
                
                if (!overlapped) {
                    Map<String, Object> entity = new LinkedHashMap<>();
                    entity.put("text", word);
                    entity.put("type", type);
                    entity.put("startPos", index);
                    entity.put("endPos", index + word.length());
                    entities.add(entity);
                    
                    // 标记已匹配位置
                    for (int i = index; i < index + word.length(); i++) {
                        matchedPositions.add(i);
                    }
                }
                index += word.length();
            }
        }
        return entities;
    }
    
    /**
     * 提取动宾结构维修动作
     */
    private void extractActionPhraseEntities(String text, List<Map<String, Object>> entities) {
        for (Pattern pattern : ACTION_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                Map<String, Object> entity = new LinkedHashMap<>();
                entity.put("text", matcher.group());
                entity.put("type", "ACTION_PHRASE");
                entity.put("startPos", matcher.start());
                entity.put("endPos", matcher.end());
                // 提取动作和对象
                if (matcher.groupCount() >= 2) {
                    entity.put("action", matcher.group(1));
                    entity.put("object", matcher.group(2));
                }
                entities.add(entity);
            }
        }
    }
    
    // ==================== 关系提取方法 ====================
    
    /**
     * 提取故障-原因关系
     */
    private void extractFaultCauseRelations(String text, List<Map<String, Object>> relations) {
        for (Pattern pattern : FAULT_CAUSE_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                Map<String, Object> relation = new LinkedHashMap<>();
                relation.put("subject", matcher.group(1));
                relation.put("predicate", "导致");
                relation.put("object", matcher.group(2));
                relation.put("relationType", "CAUSE_EFFECT");
                relation.put("evidence", matcher.group());
                relations.add(relation);
            }
        }
    }
    
    /**
     * 提取维修-结果关系
     */
    private void extractRepairResultRelations(String text, List<Map<String, Object>> relations) {
        for (Pattern pattern : REPAIR_RESULT_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                Map<String, Object> relation = new LinkedHashMap<>();
                if (matcher.groupCount() >= 2) {
                    relation.put("subject", matcher.group(1));
                    relation.put("predicate", "导致恢复");
                    relation.put("object", matcher.group(2));
                } else {
                    relation.put("subject", matcher.group(1));
                    relation.put("predicate", "完成");
                    relation.put("object", "恢复正常");
                }
                relation.put("relationType", "REPAIR_RESULT");
                relation.put("evidence", matcher.group());
                relations.add(relation);
            }
        }
    }
    
    /**
     * 基于实体推断关系三元组
     */
    private void inferRelationsFromEntities(List<Map<String, Object>> entities, 
                                            List<Map<String, Object>> relations) {
        // 提取各类实体
        List<Map<String, Object>> components = filterEntitiesByType(entities, "COMPONENT");
        List<Map<String, Object>> faults = filterEntitiesByType(entities, "FAULT_MODE");
        List<Map<String, Object>> actions = filterEntitiesByType(entities, "MAINTENANCE_ACTION");
        List<Map<String, Object>> persons = filterEntitiesByType(entities, "PERSON");
        
        // 推断：(部件, 发生, 故障)
        for (Map<String, Object> comp : components) {
            for (Map<String, Object> fault : faults) {
                // 如果位置相近（同一句子内），推断关系
                if (isNearby(comp, fault, 30)) {
                    Map<String, Object> relation = new LinkedHashMap<>();
                    relation.put("subject", comp.get("text"));
                    relation.put("predicate", "发生");
                    relation.put("object", fault.get("text"));
                    relation.put("relationType", "COMPONENT_FAULT");
                    relations.add(relation);
                }
            }
        }
        
        // 推断：(人员, 执行, 维修动作)
        for (Map<String, Object> person : persons) {
            for (Map<String, Object> action : actions) {
                if (isNearby(person, action, 50)) {
                    Map<String, Object> relation = new LinkedHashMap<>();
                    relation.put("subject", person.get("name") != null ? person.get("name") : person.get("text"));
                    relation.put("predicate", "执行");
                    relation.put("object", action.get("text"));
                    relation.put("relationType", "PERSON_ACTION");
                    relations.add(relation);
                }
            }
        }
    }
    
    // ==================== 辅助方法 ====================
    
    private List<Map<String, Object>> filterEntitiesByType(List<Map<String, Object>> entities, String type) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> entity : entities) {
            if (type.equals(entity.get("type"))) {
                result.add(entity);
            }
        }
        return result;
    }
    
    private boolean isNearby(Map<String, Object> e1, Map<String, Object> e2, int maxDistance) {
        int end1 = (Integer) e1.get("endPos");
        int start2 = (Integer) e2.get("startPos");
        int end2 = (Integer) e2.get("endPos");
        int start1 = (Integer) e1.get("startPos");
        return Math.abs(end1 - start2) <= maxDistance || Math.abs(end2 - start1) <= maxDistance;
    }
    
    private List<String> extractEntityTexts(List<Map<String, Object>> entities) {
        List<String> texts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> entity : entities) {
            String text = (String) entity.get("text");
            if (!seen.contains(text)) {
                texts.add(text);
                seen.add(text);
            }
        }
        return texts;
    }
    
    private String extractFirstTimeString(List<Map<String, Object>> entities) {
        for (Map<String, Object> entity : entities) {
            if ("TIME".equals(entity.get("type"))) {
                return (String) entity.get("text");
            }
        }
        return "未识别";
    }
    
    private String extractFirstPersonString(List<Map<String, Object>> entities) {
        for (Map<String, Object> entity : entities) {
            if ("PERSON".equals(entity.get("type"))) {
                Object name = entity.get("name");
                return name != null ? (String) name : (String) entity.get("text");
            }
        }
        return "未识别";
    }
    
    private List<Map<String, Object>> deduplicateAndSortEntities(List<Map<String, Object>> entities) {
        // 按位置排序
        entities.sort((a, b) -> {
            int posA = (Integer) a.get("startPos");
            int posB = (Integer) b.get("startPos");
            return Integer.compare(posA, posB);
        });
        
        // 去重（同位置同文本的实体）
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> entity : entities) {
            String key = entity.get("startPos") + "_" + entity.get("text");
            if (!seen.contains(key)) {
                result.add(entity);
                seen.add(key);
            }
        }
        return result;
    }

    /**
     * 解析 FMECA 知识文本
     * 
     * 基于正则+规则从文本中提取FMECA字段：
     * - 识别表格化文本中的：部件名称、故障模式、故障原因、故障影响
     * - 识别严酷度等级（I/II/III/IV 或数字）
     * - 自动计算RPN
     * 
     * @param text FMECA 知识文本
     * @return FMECA 结构化数据
     */
    public Map<String, Object> parseFMECAKnowledge(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.warn("FMECA 文本为空");
            return Collections.emptyMap();
        }

        log.info("开始解析 FMECA 知识文本，长度: {} 字符", text.length());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("originalText", text);

        // ========== 1. 提取部件名称 ==========
        String component = extractFMECAField(text, FMECA_COMPONENT_PATTERNS);
        if (component == null) {
            // 回退到词典匹配
            List<Map<String, Object>> compEntities = extractDictEntities(text, COMPONENT_DICT, "COMPONENT");
            component = compEntities.isEmpty() ? "未识别" : (String) compEntities.get(0).get("text");
        }
        result.put("component", component);

        // ========== 2. 提取故障模式 ==========
        String failureMode = extractFMECAField(text, FMECA_FAILURE_MODE_PATTERNS);
        if (failureMode == null) {
            List<Map<String, Object>> faultEntities = extractDictEntities(text, FAULT_MODE_DICT, "FAULT_MODE");
            failureMode = faultEntities.isEmpty() ? "未识别" : (String) faultEntities.get(0).get("text");
        }
        result.put("failureMode", failureMode);

        // ========== 3. 提取故障原因 ==========
        String failureCause = extractFMECAField(text, FMECA_CAUSE_PATTERNS);
        result.put("failureCause", failureCause != null ? failureCause : inferFailureCause(failureMode));

        // ========== 4. 提取故障影响 ==========
        String failureEffect = extractFMECAField(text, FMECA_EFFECT_PATTERNS);
        result.put("failureEffect", failureEffect != null ? failureEffect : inferFailureEffect(failureMode));

        // ========== 5. 提取严酷度等级 ==========
        int severity = extractSeverityLevel(text);
        result.put("severity", severity);
        result.put("severityLevel", getSeverityLevelName(severity));

        // ========== 6. 提取发生度和探测度 ==========
        int occurrence = extractOccurrence(text);
        int detection = extractDetection(text);
        result.put("occurrence", occurrence);
        result.put("detection", detection);

        // ========== 7. 计算RPN ==========
        int rpn = severity * occurrence * detection;
        result.put("rpn", rpn);
        result.put("riskLevel", getRiskLevel(rpn));

        // ========== 8. 生成建议措施 ==========
        result.put("recommendedAction", generateRecommendedAction(component, failureMode, rpn));
        result.put("note", "基于正则表达式+规则模板提取");

        log.info("FMECA 知识解析完成，组件: {}, 失效模式: {}, RPN: {}", 
                component, failureMode, rpn);
        return result;
    }
    
    // ==================== FMECA提取模式 ====================
    
    // 部件名称模式
    private static final Pattern[] FMECA_COMPONENT_PATTERNS = {
        Pattern.compile("部件[：:]​?\\s*([\\u4e00-\\u9fa5A-Za-z0-9]{2,15})"),
        Pattern.compile("组件[：:]\\s*([\\u4e00-\\u9fa5A-Za-z0-9]{2,15})"),
        Pattern.compile("设备[：:]\\s*([\\u4e00-\\u9fa5A-Za-z0-9]{2,15})"),
        Pattern.compile("名称[：:]\\s*([\\u4e00-\\u9fa5A-Za-z0-9]{2,15})")
    };
    
    // 故障模式模式
    private static final Pattern[] FMECA_FAILURE_MODE_PATTERNS = {
        Pattern.compile("故障模式[：:]\\s*([\\u4e00-\\u9fa5]{2,20})"),
        Pattern.compile("失效模式[：:]\\s*([\\u4e00-\\u9fa5]{2,20})"),
        Pattern.compile("故障类型[：:]\\s*([\\u4e00-\\u9fa5]{2,20})")
    };
    
    // 故障原因模式
    private static final Pattern[] FMECA_CAUSE_PATTERNS = {
        Pattern.compile("故障原因[：:]\\s*([\\u4e00-\\u9fa5，,、]{2,50})"),
        Pattern.compile("失效原因[：:]\\s*([\\u4e00-\\u9fa5，,、]{2,50})"),
        Pattern.compile("原因[：:]\\s*([\\u4e00-\\u9fa5，,、]{2,50})"),
        Pattern.compile("由于([\\u4e00-\\u9fa5，,]{2,30})导致")
    };
    
    // 故障影响模式
    private static final Pattern[] FMECA_EFFECT_PATTERNS = {
        Pattern.compile("故障影响[：:]\\s*([\\u4e00-\\u9fa5，,、]{2,50})"),
        Pattern.compile("失效影响[：:]\\s*([\\u4e00-\\u9fa5，,、]{2,50})"),
        Pattern.compile("影响[：:]\\s*([\\u4e00-\\u9fa5，,、]{2,50})"),
        Pattern.compile("可能导致([\\u4e00-\\u9fa5，,]{2,30})")
    };
    
    // 严酷度模式
    private static final Pattern SEVERITY_PATTERN = Pattern.compile(
        "严酷度[：:]\\s*([IⅠⅡⅢⅣV1-9]|[1-9]|10|\\d{1,2})级?"
    );
    
    // 发生度模式
    private static final Pattern OCCURRENCE_PATTERN = Pattern.compile(
        "发生度[：:]\\s*(\\d{1,2})"
    );
    
    // 探测度模式
    private static final Pattern DETECTION_PATTERN = Pattern.compile(
        "探测度[：:]\\s*(\\d{1,2})"
    );
    
    /**
     * 提取FMECA字段
     */
    private String extractFMECAField(String text, Pattern[] patterns) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return null;
    }
    
    /**
     * 提取严酷度等级
     */
    private int extractSeverityLevel(String text) {
        Matcher matcher = SEVERITY_PATTERN.matcher(text);
        if (matcher.find()) {
            String level = matcher.group(1);
            // 罗马数字转换
            switch (level) {
                case "I": case "Ⅰ": return 9;
                case "II": case "Ⅱ": return 7;
                case "III": case "Ⅲ": return 5;
                case "IV": case "Ⅳ": return 3;
                case "V": return 1;
                default:
                    try {
                        return Math.min(10, Math.max(1, Integer.parseInt(level)));
                    } catch (NumberFormatException e) {
                        return 5; // 默认中等
                    }
            }
        }
        // 基于关键词推断
        if (text.contains("灰难") || text.contains("严重伤亡")) return 10;
        if (text.contains("停机") || text.contains("安全事故")) return 8;
        if (text.contains("功能丧失") || text.contains("严重故障")) return 7;
        if (text.contains("性能下降") || text.contains("效率降低")) return 5;
        return 5; // 默认中等
    }
    
    /**
     * 提取发生度
     */
    private int extractOccurrence(String text) {
        Matcher matcher = OCCURRENCE_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return Math.min(10, Math.max(1, Integer.parseInt(matcher.group(1))));
            } catch (NumberFormatException e) {
                return 5;
            }
        }
        // 基于关键词推断
        if (text.contains("极少") || text.contains("罕见")) return 2;
        if (text.contains("偶尔") || text.contains("低概率")) return 3;
        if (text.contains("偶发")) return 4;
        if (text.contains("频繁") || text.contains("经常")) return 7;
        if (text.contains("很高") || text.contains("极高")) return 9;
        return 5; // 默认中等
    }
    
    /**
     * 提取探测度
     */
    private int extractDetection(String text) {
        Matcher matcher = DETECTION_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return Math.min(10, Math.max(1, Integer.parseInt(matcher.group(1))));
            } catch (NumberFormatException e) {
                return 5;
            }
        }
        // 基于关键词推断
        if (text.contains("易于检测") || text.contains("明显异常")) return 2;
        if (text.contains("可检测") || text.contains("有报警")) return 4;
        if (text.contains("难以检测") || text.contains("隐蔽故障")) return 8;
        if (text.contains("无法检测") || text.contains("突发")) return 10;
        return 5; // 默认中等
    }
    
    /**
     * 获取严酷度等级名称
     */
    private String getSeverityLevelName(int severity) {
        if (severity >= 9) return "I级(灰难性)";
        if (severity >= 7) return "II级(严重)";
        if (severity >= 5) return "III级(中等)";
        if (severity >= 3) return "IV级(轻微)";
        return "V级(无影响)";
    }
    
    /**
     * 获取风险等级
     */
    private String getRiskLevel(int rpn) {
        if (rpn >= 200) return "高风险(立即处理)";
        if (rpn >= 120) return "中高风险(优先处理)";
        if (rpn >= 80) return "中风险(计划处理)";
        if (rpn >= 40) return "低风险(监控)";
        return "极低风险(可接受)";
    }
    
    /**
     * 推断故障原因
     */
    private String inferFailureCause(String failureMode) {
        if (failureMode == null) return "未识别";
        if (failureMode.contains("磨损")) return "润滑不良、负载过大、运行时间过长";
        if (failureMode.contains("断裂") || failureMode.contains("裂纹")) return "疲劳过载、材料缺陷、应力集中";
        if (failureMode.contains("过热") || failureMode.contains("温度")) return "散热不良、负载过大、环境温度高";
        if (failureMode.contains("振动")) return "不平衡、松动、磨损";
        if (failureMode.contains("泄漏")) return "密封失效、老化、安装不当";
        if (failureMode.contains("腐蚀")) return "环境潮湿、化学侵蚀、防护不足";
        return "待分析";
    }
    
    /**
     * 推断故障影响
     */
    private String inferFailureEffect(String failureMode) {
        if (failureMode == null) return "未识别";
        if (failureMode.contains("磨损")) return "振动增大、噪音升高、可能导致停机";
        if (failureMode.contains("断裂")) return "传动失效、设备停机、可能引发次生故障";
        if (failureMode.contains("过热")) return "绝缘老化、性能下降、可能烧毁";
        if (failureMode.contains("振动")) return "噪音大、磨损加剧、精度下降";
        if (failureMode.contains("泄漏")) return "油压下降、污染环境、润滑不足";
        return "待评估";
    }
    
    /**
     * 生成建议措施
     */
    private String generateRecommendedAction(String component, String failureMode, int rpn) {
        StringBuilder sb = new StringBuilder();
        
        // 基于RPN确定紧迫程度
        if (rpn >= 200) sb.append("立即");
        else if (rpn >= 120) sb.append("优先");
        else sb.append("定期");
        
        // 基于故障模式确定措施
        if (failureMode != null) {
            if (failureMode.contains("磨损")) {
                sb.append("检查润滑状态，监测振动和温度，必要时更换").append(component);
            } else if (failureMode.contains("断裂") || failureMode.contains("裂纹")) {
                sb.append("进行无损检测，更换疲劳").append(component);
            } else if (failureMode.contains("过热") || failureMode.contains("温度")) {
                sb.append("清洁散热器，检查电流，监测").append(component).append("温度");
            } else if (failureMode.contains("振动")) {
                sb.append("检查动平衡，紧固连接件，消除振动源");
            } else {
                sb.append("检查并维护").append(component);
            }
        } else {
            sb.append("检查并维护").append(component != null ? component : "设备");
        }
        
        return sb.toString();
    }

    /**
     * 计算置信度（基于识别到的实体数量）
     */
    private double calculateConfidence(List<String> faults, List<String> actions, List<String> components) {
        int score = 0;
        if (!faults.isEmpty()) score += 30;
        if (faults.size() > 1) score += 10;
        if (!actions.isEmpty()) score += 30;
        if (actions.size() > 1) score += 10;
        if (!components.isEmpty()) score += 40;
        if (components.size() > 1) score += 10;
        return Math.min(1.0, score / 100.0);
    }

    /**
     * 批量解析维修记录
     * 
     * @param texts 维修记录文本列表
     * @return 解析结果列表
     */
    public List<Map<String, Object>> batchParseMaintenanceText(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        log.info("批量解析维修记录，共 {} 条", texts.size());
        List<Map<String, Object>> results = new ArrayList<>();
        for (String text : texts) {
            results.add(parseMaintenanceText(text));
        }
        return results;
    }
}
