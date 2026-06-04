package com.example.community.domain.post.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 게시글 랭킹 업데이트 이벤트
 *
 * 좋아요 수 또는 조회수 변경 시 발행되어
 * Redis Sorted Set 기반 랭킹을 업데이트합니다.
 */
@Getter
@AllArgsConstructor
public class PostRankingEvent {

    /**
     * 게시글 ID
     */
    private final Long postId;

    /**
     * 좋아요 수
     */
    private final long likeCount;

    /**
     * 조회수
     */
    private final long viewCount;
}
