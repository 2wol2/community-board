package com.example.community.domain.post.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 랭킹 점수 계산기
 * - 좋아요 수와 조회수를 기반으로 점수 계산
 * - 가중치는 application.yml에서 설정 가능
 */
@Component
@RequiredArgsConstructor
public class RankingScoreCalculator {

    private final RankingProperties properties;

    /**
     * 랭킹 점수 계산
     * @param likeCount 좋아요 수
     * @param viewCount 조회수
     * @return 계산된 점수
     */
    public double calculate(long likeCount, long viewCount) {
        return likeCount * properties.getLikeWeight()
             + viewCount * properties.getViewWeight();
    }
}
