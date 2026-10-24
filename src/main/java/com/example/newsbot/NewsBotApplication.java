package com.example.newsbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NewsBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(NewsBotApplication.class, args);
    }
}
