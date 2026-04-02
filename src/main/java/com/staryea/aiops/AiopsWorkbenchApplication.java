package com.staryea.aiops;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.staryea.aiops.mapper")
public class AiopsWorkbenchApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiopsWorkbenchApplication.class, args);
    }
}
