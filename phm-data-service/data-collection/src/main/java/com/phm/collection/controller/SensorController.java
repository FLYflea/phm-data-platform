package com.phm.collection.controller;

import com.phm.collection.entity.SensorData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 传感器数据接收控制器
 */
@Slf4j
@RestController
@RequestMapping("/sensor")
public class SensorController {

    /**
     * 接收传感器数据
     * @param sensorData 传感器数据
     * @return 处理结果
     */
    @PostMapping
    public String receiveSensorData(@RequestBody SensorData sensorData) {
        log.info("接收到传感器数据: deviceId={}, sensorType={}, value={}, timestamp={}",
                sensorData.getDeviceId(),
                sensorData.getSensorType(),
                sensorData.getValue(),
                sensorData.getTimestamp());
        
        // TODO: 后续存入数据库
        
        return "数据接收成功: " + sensorData.getDeviceId();
    }
}
