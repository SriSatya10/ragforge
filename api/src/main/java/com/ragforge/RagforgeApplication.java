package com.ragforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RagforgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagforgeApplication.class, args);
    }
}
