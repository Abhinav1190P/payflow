package com.payflow.common.config;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;

@Configuration
public class AsyncConfig implements AsyncConfigurer {

    // Concept: virtual threads (Java 21). Each task gets a cheap virtual thread, so blocking I/O
    // such as a database call does not tie up a scarce platform thread.
    @Bean(destroyMethod = "close")
    public ExecutorService ioExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    // @Async methods run on the same virtual-thread executor.
    @Override
    public Executor getAsyncExecutor() {
        return ioExecutor();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
