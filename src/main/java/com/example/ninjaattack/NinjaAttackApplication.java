package com.example.ninjaattack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling; // 1. 导入

@EnableScheduling // 2. 添加这个注解
@SpringBootApplication
public class NinjaAttackApplication {

    public static void main(String[] args) {
        SpringApplication.run(NinjaAttackApplication.class, args);
    }

}