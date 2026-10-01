package com.devpilot.common.async;

import com.devpilot.common.config.DevPilotProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 AI 작업 실행기 (docs/03 §3.1·§5.3, BL-FND-23). {@code aiTaskExecutor}: core 2, max 2, queue
 * 20({@code devpilot.ai.async}). 큐가 가득 차 제출이 거절되면 {@code TaskRejectedException}이다 — 제출한 쪽이 그 리소스를
 * 즉시 {@code FAILED(INTERNAL_ERROR)}로 바꾸고 WARN 로그를 남긴다.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig {

    /**
     * {@code @Async("aiTaskExecutor")} 대상 (ARCH-05: {@code ..infrastructure..}의 {@code *Task}만).
     */
    public static final String AI_TASK_EXECUTOR = "aiTaskExecutor";

    @Bean(name = AI_TASK_EXECUTOR)
    public ThreadPoolTaskExecutor aiTaskExecutor(DevPilotProperties properties) {
        DevPilotProperties.Async async = properties.ai().async();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(async.corePoolSize());
        executor.setMaxPoolSize(async.maxPoolSize());
        executor.setQueueCapacity(async.queueCapacity());
        executor.setThreadNamePrefix("ai-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
