package com.poc.composite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class CompositeServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CompositeServiceApplication.class, args);
    }
}
