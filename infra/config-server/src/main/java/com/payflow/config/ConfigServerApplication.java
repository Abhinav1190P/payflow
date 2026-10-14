package com.payflow.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

// Concept: centralised configuration. Services fetch "<name>.yml" (+ shared application.yml) from here at
// startup, so tunables (timeouts, limits) change in one place instead of in every jar.
// The native backend reads files from the classpath; in production this would point at a Git repository.
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
