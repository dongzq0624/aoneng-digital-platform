package com.example.rag.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 文档处理线程池：把解析、分块、嵌入、向量化等耗时操作从请求线程剥离。
 * 关闭时不等待任务完成，避免异常文档阻塞整体下线。
 */
@Configuration
public class DocumentProcessingConfig {

    /**
     * 核心 2 / 最大 4 / 队列 32 的有界线程池，足以覆盖常规并发上传。
     * 队列满后新提交任务将由调用方决定是降级还是拒绝。
     */
    @Bean(name = "documentProcessingExecutor")
    ThreadPoolTaskExecutor documentProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("document-processing-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
