package com.possapp.backend.seeder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * CommandLineRunner for automatic database seeding.
 * 
 * Activate with: spring.profiles.active=seed
 * Or add 'seed' to active profiles: spring.profiles.active=dev,seed
 */
@Slf4j
@Configuration
@Profile({"dev", "development", "test", "seed"})
public class SeederRunner {

    /**
     * Runs automatically when 'seed' profile is active.
     * Seeds the database on application startup.
     */
    @Bean
    @Profile("seed")
    public CommandLineRunner runSeeder(DatabaseSeeder databaseSeeder) {
        return args -> {
            log.info("🌱 Seed profile active - starting automatic database seeding...");
            databaseSeeder.seedAll();
            log.info("🌱 Automatic seeding completed!");
        };
    }
}
