package com.example.kitchen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@SpringBootApplication
@EnableR2dbcRepositories(basePackages = "com.example.kitchen.repository")
public class KitchenChallengeApplication {

    public static void main(String[] args) {
        SpringApplication.run(KitchenChallengeApplication.class, args);
    }

}
