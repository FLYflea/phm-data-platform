package com.phm.storage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

/**
 * 数据存储服务启动类
 * 
 * 支持双存储引擎：
 * - PostgreSQL（JPA）：时序数据存储
 * - Neo4j：知识图谱存储
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.phm.storage.repository.timeseries")
@EnableNeo4jRepositories(basePackages = "com.phm.storage.repository.graph")
public class DataStorageApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataStorageApplication.class, args);
        System.out.println("数据存储服务启动成功！端口: 8103");
        System.out.println("支持的存储引擎: PostgreSQL(时序数据)、Neo4j(知识图谱)");
    }
}
