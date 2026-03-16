package com.phm.storage.config;

import org.springframework.context.annotation.Configuration;

/**
 * 数据源配置
 * 
 * 简化版：使用 Spring Boot 自动配置
 * 数据源配置通过 application.yml 完成
 * Neo4j 通过 application.yml 自动配置
 */
@Configuration
public class DataSourceConfig {
    // 使用 Spring Boot 自动配置，无需手动创建 DataSource Bean
}
