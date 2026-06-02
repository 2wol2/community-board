package com.example.community.domain.post.ranking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Redis Sorted Set 기반 게시글 랭킹 서비스
 *
 * Key: "post:ranking"
 * Score: likeCount * 10 + viewCount * 1
 * Value: postId (문자열)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostRankingService {

    private static final String RANKING_KEY = "post:ranking";

    private final StringRedisTemplate redisTemplate;

    /**
     * 게시글 점수 업데이트
     *
     * @param postId 게시글 ID
     * @param score  점수
     */
    public void updateScore(Long postId, double score) {
        try {
            redisTemplate.opsForZSet().add(RANKING_KEY, String.valueOf(postId), score);
            log.debug("[랭킹] 점수 업데이트 - postId: {}, score: {}", postId, score);
        } catch (Exception e) {
            // Redis 장애 시 로그만 남기고 예외 전파 안 함 (부가 기능이므로)
            log.error("[랭킹] 점수 업데이트 실패 - postId: {}, score: {}", postId, score, e);
        }
    }

    /**
     * Top N 인기 게시글 조회 (내림차순)
     *
     * @param topN 조회할 개수
     * @return 게시글 ID 리스트 (점수 높은 순)
     */
    public Set<Long> getTopRanking(int topN) {
        try {
            // ZREVRANGE: 점수 높은 순으로 조회
            Set<String> postIds = redisTemplate.opsForZSet()
                    .reverseRange(RANKING_KEY, 0, topN - 1);

            if (postIds == null || postIds.isEmpty()) {
                log.warn("[랭킹] 랭킹 데이터 없음");
                return Set.of();
            }

            return postIds.stream()
                    .map(Long::valueOf)
                    .collect(Collectors.toSet());

        } catch (Exception e) {
            log.error("[랭킹] Top {} 조회 실패", topN, e);
            return Set.of();
        }
    }

    /**
     * Top N 인기 게시글 조회 (점수 포함)
     *
     * @param topN 조회할 개수
     * @return 게시글 ID와 점수 Set
     */
    public Set<ZSetOperations.TypedTuple<String>> getTopRankingWithScores(int topN) {
        try {
            Set<ZSetOperations.TypedTuple<String>> rankingWithScores =
                    redisTemplate.opsForZSet().reverseRangeWithScores(RANKING_KEY, 0, topN - 1);

            if (rankingWithScores == null || rankingWithScores.isEmpty()) {
                log.warn("[랭킹] 랭킹 데이터 없음");
                return Set.of();
            }

            return rankingWithScores;

        } catch (Exception e) {
            log.error("[랭킹] Top {} 조회 실패 (점수 포함)", topN, e);
            return Set.of();
        }
    }

    /**
     * 게시글 삭제 시 랭킹에서 제거
     *
     * @param postId 게시글 ID
     */
    public void removePost(Long postId) {
        try {
            redisTemplate.opsForZSet().remove(RANKING_KEY, String.valueOf(postId));
            log.debug("[랭킹] 게시글 제거 - postId: {}", postId);
        } catch (Exception e) {
            log.error("[랭킹] 게시글 제거 실패 - postId: {}", postId, e);
        }
    }

    /**
     * 전체 랭킹 개수 조회
     *
     * @return 랭킹에 등록된 게시글 수
     */
    public Long getRankingSize() {
        try {
            return redisTemplate.opsForZSet().size(RANKING_KEY);
        } catch (Exception e) {
            log.error("[랭킹] 크기 조회 실패", e);
            return 0L;
        }
    }

    /**
     * 특정 게시글의 랭킹 순위 조회 (1부터 시작)
     *
     * @param postId 게시글 ID
     * @return 순위 (1위 = 1, 없으면 null)
     */
    public Long getRank(Long postId) {
        try {
            Long rank = redisTemplate.opsForZSet().reverseRank(RANKING_KEY, String.valueOf(postId));
            return rank != null ? rank + 1 : null;
        } catch (Exception e) {
            log.error("[랭킹] 순위 조회 실패 - postId: {}", postId, e);
            return null;
        }
    }

    /**
     * 특정 게시글의 점수 조회
     *
     * @param postId 게시글 ID
     * @return 점수 (없으면 null)
     */
    public Double getScore(Long postId) {
        try {
            return redisTemplate.opsForZSet().score(RANKING_KEY, String.valueOf(postId));
        } catch (Exception e) {
            log.error("[랭킹] 점수 조회 실패 - postId: {}", postId, e);
            return null;
        }
    }
}
