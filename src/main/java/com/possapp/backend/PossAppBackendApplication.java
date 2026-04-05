package com.possapp.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@ServletComponentScan
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableScheduling
public class PossAppBackendApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(PossAppBackendApplication.class, args);
    }
}
