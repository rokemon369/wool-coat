package org.example.woolcoat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步/流式任务线程池配置（SSE 流式接口等使用，避免 new Thread）
 */
@Configuration
public class AsyncConfig {

    /**
     * SSE 流式任务专用线程池（有界队列，避免 OOM）
     */
    @Bean("sseStreamExecutor")
    public Executor sseStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("sse-stream-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
