package com.phm.storage.config;

import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.neo4j.core.Neo4jTemplate;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Neo4j 配置类
 * 
 * 配置 Neo4j 事务管理器，用于知识图谱操作
 * 注意：当同时存在 JPA 和 Neo4j 时，需要显式配置 Neo4j 事务管理器
 */
@Configuration
@EnableTransactionManagement
public class Neo4jConfig {

    /**
     * Neo4j 事务管理器
     * 
     * @param driver Neo4j 驱动
     * @return 事务管理器
     */
    @Bean("neo4jTransactionManager")
    public Neo4jTransactionManager neo4jTransactionManager(Driver driver) {
        return new Neo4jTransactionManager(driver);
    }

    /**
     * Neo4j 客户端
     * 
     * @param driver Neo4j 驱动
     * @return Neo4j 客户端
     */
    @Bean
    public Neo4jClient neo4jClient(Driver driver) {
        return Neo4jClient.create(driver);
    }

    /**
     * Neo4j 模板
     * 
     * @param neo4jClient Neo4j 客户端
     * @param driver Neo4j 驱动
     * @return Neo4j 模板
     */
    @Bean
    public Neo4jTemplate neo4jTemplate(Neo4jClient neo4jClient, Driver driver) {
        return new Neo4jTemplate(neo4jClient, neo4jTransactionManager(driver));
    }
}
