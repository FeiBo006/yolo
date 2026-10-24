package com.yuanyu.safety;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 基于 YOLO 的工地安全行为实时检测预警系统 - 业务后端入口。
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan("com.yuanyu.safety.mapper")
@EnableScheduling
public class SafetyApplication {

    public static void main(String[] args) {
        SpringApplication.run(SafetyApplication.class, args);
    }
}
