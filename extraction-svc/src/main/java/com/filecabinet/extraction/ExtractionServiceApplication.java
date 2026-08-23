package com.filecabinet.extraction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ExtractionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExtractionServiceApplication.class, args);
    }
}
