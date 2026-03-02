package com.skilltracker.student_skill_tracker.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "rishiAgentExecutor")
    public Executor rishiAgentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Limit to exactly 1 concurrent AI processing thread to save CPU/RAM on Render
        // Free Tier
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        // Allows up to 100 queued questions before rejecting new ones
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("RishiQueue-");
        executor.initialize();
        return executor;
    }
}
