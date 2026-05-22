package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;

/**
 * 异步任务线程池配置。
 *
 * <p>使用 {@code @Async("taskExecutor")} 在业务方法上开启异步执行。
 * 生产环境下，Spring Boot 默认的 {@link org.springframework.core.task.SimpleAsyncTaskConnector}
 * 每个任务新建线程，不可控，必须替换。</p>
 */
@EnableAsync
@Configuration
public class ThreadPoolConfig {

    @Bean("taskExecutor")
    public Executor taskExecutor() {
        var executor = new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();

        // 核心线程数：平时保留的线程数
        executor.setCorePoolSize(5);
        // 最大线程数：高峰期最多开多少线程
        executor.setMaxPoolSize(20);
        // 任务队列容量：核心线程满了，先排队
        executor.setQueueCapacity(100);
        // 线程名前缀：方便排查线程归属
        executor.setThreadNamePrefix("task-");

        // 拒绝策略：队列满了 + 线程到上限 → 由调用方线程执行（不让任务丢）
        executor.setRejectedExecutionHandler(new CallerRunsPolicy());

        executor.initialize();
        return executor;
    }
}
