package com.filecabinet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class FileCabinetApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileCabinetApplication.class, args);
    }

}
