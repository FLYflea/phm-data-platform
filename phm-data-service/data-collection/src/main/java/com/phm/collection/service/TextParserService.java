package com.phm.collection.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本解析服务
 * 
 * 功能：
 * - 解析维修记录文本，提取故障模式和维修措施
 * - 解析 FMECA 知识文本，提取失效模式信息
 * 
 * TODO: BERT-BiLSTM-CRF 待接入
 * - 当前使用正则表达式进行简单匹配
 * - 实际应使用 BERT-BiLSTM-CRF 深度学习模型进行命名实体识别（NER）
 * - 需构建领域专用语料库和标注数据
 */
@Slf4j
@Service
public class TextParserService {

    // 故障模式关键词库
    private static final List<String> FAULT_PATTERNS = Arrays.asList(
            "磨损", "断裂", "裂纹", "腐蚀", "变形", "松动", "卡死", "过热",
            "异响", "振动", "泄漏", "堵塞", "短路", "断路", "老化", "失效",
            "烧蚀", "剥落", "点蚀", "胶合"
    );

    // 维修措施关键词库
    private static final List<String> MAINTENANCE_ACTIONS = Arrays.asList(
            "更换", "修复", "清洗", "润滑", "调整", "紧固", "校准", "检测",
            "保养", "维修", "替换", "补焊", "打磨", "抛光", "涂覆", "除锈"
    );

    // 设备部件关键词库
    private static final List<String> COMPONENT_PATTERNS = Arrays.asList(
            "轴承", "齿轮", "电机", "联轴器", "皮带", "链条", "阀门", "泵体",
            "叶轮", "轴", "密封圈", "传感器", "控制器", "开关", "接触器"
    );

    /**
     * 解析维修记录文本，提取故障模式和维修措施
     * 
     * TODO: BERT-BiLSTM-CRF 待接入
     * - 当前使用正则表达式匹配关键词
     * - 实际应使用 BERT-BiLSTM-CRF 进行序列标注，识别实体边界
     * 
     * @param text 维修记录文本
     * @return 提取结果，包含 faultPattern, maintenanceAction, component, note
     */
    public Map<String, Object> parseMaintenanceText(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.warn("维修记录文本为空");
            return Collections.emptyMap();
        }

        log.info("开始解析维修记录文本，长度: {} 字符", text.length());

        Map<String, Object> result = new HashMap<>();
        result.put("originalText", text);

        // TODO: BERT-BiLSTM-CRF 待接入 - 当前使用简单正则匹配
        // 实际应进行以下步骤：
        // 1. 文本预处理（分词、去停用词）
        // 2. BERT 编码获取上下文表示
        // 3. BiLSTM 捕捉序列特征
        // 4. CRF 解码获取最优标签序列
        // 5. 提取命名实体（故障模式、维修措施、部件等）

        // 提取故障模式
        List<String> detectedFaults = extractKeywords(text, FAULT_PATTERNS);
        result.put("faultPatterns", detectedFaults);
        result.put("faultPattern", detectedFaults.isEmpty() ? "未识别" : detectedFaults.get(0));

        // 提取维修措施
        List<String> detectedActions = extractKeywords(text, MAINTENANCE_ACTIONS);
        result.put("maintenanceActions", detectedActions);
        result.put("maintenanceAction", detectedActions.isEmpty() ? "未识别" : detectedActions.get(0));

        // 提取涉及部件
        List<String> detectedComponents = extractKeywords(text, COMPONENT_PATTERNS);
        result.put("components", detectedComponents);
        result.put("component", detectedComponents.isEmpty() ? "未识别" : detectedComponents.get(0));

        // 提取时间信息（简单正则）
        String timeInfo = extractTimeInfo(text);
        result.put("maintenanceTime", timeInfo);

        // 提取人员信息
        String personnel = extractPersonnel(text);
        result.put("maintenancePerson", personnel);

        // 备注
        result.put("note", "基于正则规则提取，BERT-BiLSTM-CRF 待接入");
        result.put("confidence", calculateConfidence(detectedFaults, detectedActions, detectedComponents));

        log.info("维修记录解析完成，识别故障模式: {}, 维修措施: {}", 
                detectedFaults, detectedActions);
        return result;
    }

    /**
     * 解析 FMECA 知识文本
     * 
     * TODO: BERT-BiLSTM-CRF 待接入
     * - 当前返回模拟数据
     * - 实际应从非结构化 FMECA 文档中提取结构化信息
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

        // TODO: BERT-BiLSTM-CRF 待接入 - 实际应进行：
        // 1. 识别失效模式（Failure Mode）
        // 2. 识别失效原因（Failure Cause）
        // 3. 识别失效影响（Failure Effect）
        // 4. 提取严重度（Severity）、发生度（Occurrence）、探测度（Detection）
        // 5. 计算风险优先数（RPN）

        Map<String, Object> result = new HashMap<>();
        result.put("originalText", text);
        result.put("note", "FMECA 解析模拟数据，BERT-BiLSTM-CRF 待接入");

        // 模拟解析结果 - 基于输入文本长度随机生成不同结果
        int seed = text.length() % 3;
        switch (seed) {
            case 0:
                // 轴承磨损案例
                result.put("component", "主轴承");
                result.put("failureMode", "磨损");
                result.put("failureCause", "润滑不良、负载过大");
                result.put("failureEffect", "振动增大、温度升高、可能导致停机");
                result.put("severity", 8);
                result.put("occurrence", 6);
                result.put("detection", 4);
                result.put("rpn", 192); // 8 * 6 * 4
                result.put("recommendedAction", "定期检查润滑状态，监测振动和温度");
                break;
            case 1:
                // 齿轮断裂案例
                result.put("component", "齿轮");
                result.put("failureMode", "断裂");
                result.put("failureCause", "疲劳过载、材料缺陷");
                result.put("failureEffect", "传动失效、设备停机");
                result.put("severity", 9);
                result.put("occurrence", 3);
                result.put("detection", 5);
                result.put("rpn", 135); // 9 * 3 * 5
                result.put("recommendedAction", "定期无损检测，更换疲劳齿轮");
                break;
            default:
                // 电机过热案例
                result.put("component", "电机");
                result.put("failureMode", "过热");
                result.put("failureCause", "散热不良、电流过大");
                result.put("failureEffect", "绝缘老化、电机烧毁");
                result.put("severity", 7);
                result.put("occurrence", 5);
                result.put("detection", 3);
                result.put("rpn", 105); // 7 * 5 * 3
                result.put("recommendedAction", "清洁散热器，监测电流和温度");
                break;
        }

        log.info("FMECA 知识解析完成，组件: {}, 失效模式: {}", 
                result.get("component"), result.get("failureMode"));
        return result;
    }

    /**
     * 从文本中提取关键词
     * 
     * @param text 原始文本
     * @param keywords 关键词列表
     * @return 匹配到的关键词列表
     */
    private List<String> extractKeywords(String text, List<String> keywords) {
        List<String> found = new ArrayList<>();
        for (String keyword : keywords) {
            // 使用正则匹配完整词，避免部分匹配
            String regex = "(?i)" + Pattern.quote(keyword);
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find() && !found.contains(keyword)) {
                found.add(keyword);
            }
        }
        return found;
    }

    /**
     * 提取时间信息
     * 
     * @param text 原始文本
     * @return 提取到的时间字符串
     */
    private String extractTimeInfo(String text) {
        // 匹配常见时间格式：2024-01-15、2024年1月15日、2024/01/15
        Pattern pattern = Pattern.compile("(\\d{4}[-/年]\\d{1,2}[-/月]\\d{1,2}[日]?|\\d{4}[-/]\\d{1,2}[-/]\\d{1,2})");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "未识别";
    }

    /**
     * 提取维修人员信息
     * 
     * @param text 原始文本
     * @return 提取到的人员姓名
     */
    private String extractPersonnel(String text) {
        // 匹配"维修人员：XXX"、"负责人：XXX"等格式
        Pattern pattern = Pattern.compile("(?:维修人员|负责人|操作人|工程师)[：:]\\s*([\\u4e00-\\u9fa5]{2,4})");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "未识别";
    }

    /**
     * 计算置信度（基于识别到的实体数量）
     * 
     * @param faults 故障模式列表
     * @param actions 维修措施列表
     * @param components 部件列表
     * @return 置信度（0.0 ~ 1.0）
     */
    private double calculateConfidence(List<String> faults, List<String> actions, List<String> components) {
        int score = 0;
        if (!faults.isEmpty()) score += 30;
        if (!actions.isEmpty()) score += 30;
        if (!components.isEmpty()) score += 40;
        return score / 100.0;
    }

    /**
     * 批量解析维修记录
     * 
     * @param texts 维修记录文本列表
     * @return 解析结果列表
     */
    public List<Map<String, Object>> batchParseMaintenanceText(List<String> texts) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (String text : texts) {
            results.add(parseMaintenanceText(text));
        }
        return results;
    }
}
