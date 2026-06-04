package com.example.community.domain.post.ranking;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 랭킹 점수 계산 가중치 설정
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ranking.score")
public class RankingProperties {

    /**
     * 좋아요 가중치 (기본값: 10.0)
     */
    private double likeWeight = 10.0;

    /**
     * 조회수 가중치 (기본값: 1.0)
     */
    private double viewWeight = 1.0;
}
