package com.phm.collection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 数据采集服务启动类
 */
@SpringBootApplication
public class DataCollectionApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataCollectionApplication.class, args);
        System.out.println("数据采集服务启动成功！端口: 8101");
    }
}
