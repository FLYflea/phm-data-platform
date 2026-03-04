package com.phm.computation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 数据计算服务启动类
 */
@SpringBootApplication
public class DataComputationApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataComputationApplication.class, args);
        System.out.println("数据计算服务启动成功！端口: 8102");
    }
}
