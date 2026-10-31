package com.skelar.defuse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DefuseApplication {
    public static void main(String[] args) {
        SpringApplication.run(DefuseApplication.class, args);
    }
}
