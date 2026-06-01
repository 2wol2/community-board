package com.example.community.domain.user;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

@Getter
@AllArgsConstructor
@RedisHash("refreshToken")
public class RefreshToken {

    @Id
    private Long userId;

    private String token;

    @TimeToLive
    private Long expiration;

    public static RefreshToken of(Long userId, String token, Long expirationMillis) {
        // JWT는 milliseconds 단위, Redis TTL은 seconds 단위
        // 단위 변환 책임을 RefreshToken이 가짐
        return new RefreshToken(userId, token, expirationMillis / 1000);
    }
}
