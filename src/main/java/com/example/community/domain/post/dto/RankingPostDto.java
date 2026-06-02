package com.example.community.domain.post.dto;

import com.example.community.domain.post.Post;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 인기 게시글 응답 DTO
 */
@Getter
@AllArgsConstructor
public class RankingPostDto {

    /**
     * 게시글 ID
     */
    private Long id;

    /**
     * 제목
     */
    private String title;

    /**
     * 좋아요 수
     */
    private long likeCount;

    /**
     * 조회수
     */
    private long viewCount;

    /**
     * 랭킹 점수
     */
    private double score;

    /**
     * 순위 (1위부터 시작)
     */
    private int rank;

    public static RankingPostDto of(Post post, long likeCount, double score, int rank) {
        return new RankingPostDto(
                post.getId(),
                post.getTitle(),
                likeCount,
                post.getViewCount(),
                score,
                rank
        );
    }
}
