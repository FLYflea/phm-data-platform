package com.phm.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PhmGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(PhmGatewayApplication.class, args);
        System.out.println("网关启动成功！访问 http://localhost:8080");
    }
}