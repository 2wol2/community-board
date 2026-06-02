package com.example.community.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 처리 설정
 *
 * 이벤트 리스너를 비동기로 처리하여
 * 메인 트랜잭션 응답 속도에 영향을 주지 않도록 합니다.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * 비동기 작업용 스레드 풀 설정
     *
     * - corePoolSize: 기본 스레드 수 2개
     * - maxPoolSize: 최대 스레드 수 10개
     * - queueCapacity: 대기 큐 100개
     * - threadNamePrefix: 로그 추적용 스레드 이름
     */
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-ranking-");
        executor.initialize();
        return executor;
    }

    /**
     * 비동기 작업 중 예외 발생 시 처리
     *
     * Redis 장애 등으로 랭킹 업데이트 실패 시
     * 로그만 남기고 메인 서비스는 정상 동작하도록 합니다.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("[비동기 예외] 메서드: {}, 파라미터: {}", method.getName(), params, ex);
        };
    }
}
