package com.example.rag;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan({
    "com.example.rag.domain.auth.mapper",
    "com.example.rag.domain.kb.mapper",
    "com.example.rag.domain.chat.mapper",
    "com.example.rag.domain.audit.mapper"
})
@EnableAsync
public class RagApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }
}
