package com.phm.computation.controller;

import com.phm.computation.entity.SensorData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据融合控制器
 */
@Slf4j
@RestController
@RequestMapping("/fusion")
public class FusionController {

    /**
     * 数据融合：接收多传感器数据，计算平均值返回
     * @param dataList 传感器数据列表
     * @return 融合后的结果（按传感器类型分组的平均值）
     */
    @PostMapping
    public Map<String, Double> dataFusion(@RequestBody List<SensorData> dataList) {
        log.info("接收到数据融合请求，数据条数: {}", dataList.size());
        
        // 按传感器类型分组，计算平均值（简化版算法）
        Map<String, Double> fusionResult = dataList.stream()
                .collect(Collectors.groupingBy(
                        SensorData::getSensorType,
                        Collectors.averagingDouble(SensorData::getValue)
                ));
        
        log.info("数据融合完成，结果: {}", fusionResult);
        return fusionResult;
    }
}
