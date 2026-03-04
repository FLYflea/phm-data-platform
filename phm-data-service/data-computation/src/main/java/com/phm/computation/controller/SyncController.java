package com.phm.computation.controller;

import com.phm.computation.entity.SensorData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

/**
 * 时间同步控制器
 */
@Slf4j
@RestController
@RequestMapping("/sync")
public class SyncController {

    /**
     * 时间同步：接收数据列表，按时间戳排序后返回
     * @param dataList 传感器数据列表
     * @return 按时间戳排序后的数据列表
     */
    @PostMapping
    public List<SensorData> timeSync(@RequestBody List<SensorData> dataList) {
        log.info("接收到时间同步请求，数据条数: {}", dataList.size());
        
        // 按时间戳排序（简化版算法）
        List<SensorData> sortedList = dataList.stream()
                .sorted(Comparator.comparing(SensorData::getTimestamp))
                .toList();
        
        log.info("时间同步完成，排序后数据条数: {}", sortedList.size());
        return sortedList;
    }
}
