package com.example.community.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String LIKE_COUNT_CACHE = "likeCount";

    @Value("${cache.default-ttl}")
    private long defaultTtl;

    @Value("${cache.like-count-ttl}")
    private long likeCountTtl;

    /**
     * 프로덕션/개발 환경용 Redis 캐시 매니저
     */
    @Bean("cacheManager")
    @Profile("!test")
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        // 기본 캐시 설정 (모든 캐시에 공통 적용)
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(defaultTtl))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(RedisSerializer.json())
                );

        // 캐시별 개별 설정 (defaultConfig 상속 후 TTL만 변경)
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put(
                LIKE_COUNT_CACHE,
                defaultConfig.entryTtl(Duration.ofMinutes(likeCountTtl))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    /**
     * 테스트 환경용 간단한 메모리 캐시 매니저
     *
     * 참고: ConcurrentMapCacheManager는 TTL을 지원하지 않습니다.
     * 테스트 환경에서는 캐시 동작(히트/미스/삭제)만 검증하므로
     * TTL이 없어도 테스트 목적에는 충분합니다.
     */
    @Bean("cacheManager")
    @Profile("test")
    public CacheManager memoryCacheManager() {
        return new ConcurrentMapCacheManager(LIKE_COUNT_CACHE);
    }

    /**
     * Redis 장애 시 애플리케이션이 터지지 않고 DB로 폴백하도록 설정
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler();
    }
}
