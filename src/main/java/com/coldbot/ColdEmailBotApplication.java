package com.coldbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ColdEmailBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(ColdEmailBotApplication.class, args);
    }
}
