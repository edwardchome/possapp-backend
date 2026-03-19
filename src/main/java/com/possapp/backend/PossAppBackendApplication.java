package com.possapp.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class PossAppBackendApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(PossAppBackendApplication.class, args);
    }
}
