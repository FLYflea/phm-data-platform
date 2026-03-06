package com.phm.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 数据服务层启动类
 * 
 * 职责：
 * - 统一查询接口（调用存储层）
 * - 可视化数据接口（图表数据格式转换）
 * 
 * 架构位置：第四层（服务层）
 * - 向下调用：data-storage (8103)
 * - 向上提供：统一查询、可视化数据
 */
@SpringBootApplication
public class DataServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(DataServiceApplication.class, args);
        System.out.println("数据服务层启动成功！端口: 8104");
        System.out.println("提供接口: /service/query, /service/chart");
    }
}
