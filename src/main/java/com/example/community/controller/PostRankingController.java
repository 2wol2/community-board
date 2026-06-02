package com.example.community.controller;

import com.example.community.domain.like.PostLikeRepository;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import com.example.community.domain.post.dto.RankingPostDto;
import com.example.community.domain.post.ranking.PostRankingService;
import com.example.community.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 게시글 랭킹 API 컨트롤러
 *
 * Redis Sorted Set 기반 인기 게시글 조회
 */
@Slf4j
@Tag(name = "게시글 랭킹", description = "인기 게시글 랭킹 API")
@RestController
@RequestMapping("/api/posts/ranking")
@RequiredArgsConstructor
public class PostRankingController {

    private final PostRankingService rankingService;
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;

    /**
     * Top N 인기 게시글 조회 (Redis 기반)
     *
     * @param topN 조회할 개수 (기본 10개)
     * @return 인기 게시글 목록 (점수 높은 순)
     */
    @Operation(summary = "인기 게시글 Top N 조회", description = "Redis Sorted Set 기반으로 인기 게시글을 조회합니다")
    @GetMapping
    public ApiResponse<List<RankingPostDto>> getTopRanking(
            @RequestParam(defaultValue = "10") int topN) {

        Set<ZSetOperations.TypedTuple<String>> rankingWithScores =
                rankingService.getTopRankingWithScores(topN);

        // 1. postId 목록 추출
        List<Long> postIds = rankingWithScores.stream()
                .map(tuple -> Long.valueOf(tuple.getValue()))
                .toList();

        if (postIds.isEmpty()) {
            return ApiResponse.success(List.of());
        }

        // 2. Post 일괄 조회 (1개 쿼리)
        List<Post> posts = postRepository.findAllById(postIds);
        Map<Long, Post> postMap = posts.stream()
                .collect(Collectors.toMap(Post::getId, post -> post));

        // 3. 좋아요 수 일괄 조회 (1개 쿼리)
        Map<Long, Long> likeCountMap = postLikeRepository.countLikesByPostIdsAsMap(postIds);

        // 4. 결과 조합
        List<RankingPostDto> result = new ArrayList<>();
        int rank = 1;

        for (ZSetOperations.TypedTuple<String> tuple : rankingWithScores) {
            Long postId = Long.valueOf(tuple.getValue());
            Double score = tuple.getScore();

            Post post = postMap.get(postId);
            if (post == null) {
                continue;
            }

            long likeCount = likeCountMap.getOrDefault(postId, 0L);

            RankingPostDto dto = RankingPostDto.of(post, likeCount, score, rank++);
            result.add(dto);
        }

        log.info("[랭킹 API] Top {} 조회 완료: {}건 (쿼리 2개)", topN, result.size());

        return ApiResponse.success(result);
    }

    /**
     * DB 기반 인기 게시글 조회 (성능 비교용)
     *
     * @param topN 조회할 개수 (기본 10개)
     * @return 인기 게시글 목록
     */
    @Operation(summary = "인기 게시글 Top N 조회 (DB 방식)", description = "성능 비교를 위한 DB 기반 조회")
    @GetMapping("/db")
    public ApiResponse<List<RankingPostDto>> getTopRankingFromDB(
            @RequestParam(defaultValue = "10") int topN) {

        // 1. 모든 게시글 조회 (1개 쿼리)
        List<Post> posts = postRepository.findAll();

        if (posts.isEmpty()) {
            return ApiResponse.success(List.of());
        }

        // 2. 모든 게시글의 좋아요 수 일괄 조회 (1개 쿼리)
        List<Long> postIds = posts.stream()
                .map(Post::getId)
                .toList();
        Map<Long, Long> likeCountMap = postLikeRepository.countLikesByPostIdsAsMap(postIds);

        // 3. 점수 계산
        List<PostWithScore> postsWithScores = new ArrayList<>();
        for (Post post : posts) {
            long likeCount = likeCountMap.getOrDefault(post.getId(), 0L);
            long viewCount = post.getViewCount();
            double score = likeCount * 10.0 + viewCount * 1.0;

            postsWithScores.add(new PostWithScore(post, likeCount, score));
        }

        // 4. 점수 높은 순 정렬
        postsWithScores.sort((a, b) -> Double.compare(b.score, a.score));

        // 5. Top N만 추출 및 순위 부여
        List<RankingPostDto> rankedPosts = new ArrayList<>();
        for (int i = 0; i < Math.min(topN, postsWithScores.size()); i++) {
            PostWithScore pws = postsWithScores.get(i);
            rankedPosts.add(RankingPostDto.of(pws.post, pws.likeCount, pws.score, i + 1));
        }

        log.info("[랭킹 API - DB] Top {} 조회 완료: {}건 (쿼리 2개)", topN, rankedPosts.size());

        return ApiResponse.success(rankedPosts);
    }

    /**
     * 게시글 + 점수 래퍼 클래스
     */
    private static class PostWithScore {
        Post post;
        long likeCount;
        double score;

        PostWithScore(Post post, long likeCount, double score) {
            this.post = post;
            this.likeCount = likeCount;
            this.score = score;
        }
    }
}
