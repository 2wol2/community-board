package com.example.community.domain.post.event;

import com.example.community.domain.post.ranking.PostRankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 게시글 랭킹 이벤트 리스너
 *
 * @Async를 사용하여 비동기로 처리됩니다.
 * - 메인 트랜잭션 응답 속도에 영향 없음
 * - Redis 장애 시에도 좋아요/조회수 기능은 정상 동작
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostRankingEventListener {

    private final PostRankingService rankingService;

    /**
     * 게시글 랭킹 업데이트 이벤트 처리
     *
     * @Async 덕분에 별도 스레드에서 실행되어
     * 좋아요/조회수 API 응답 속도에 영향을 주지 않습니다.
     *
     * @param event 랭킹 이벤트
     */
    @Async
    @EventListener
    public void handlePostRankingEvent(PostRankingEvent event) {
        try {
            double score = event.calculateScore();

            log.info("[랭킹 이벤트] postId: {}, likeCount: {}, viewCount: {}, score: {}",
                    event.getPostId(), event.getLikeCount(), event.getViewCount(), score);

            rankingService.updateScore(event.getPostId(), score);

        } catch (Exception e) {
            // 랭킹은 부가 기능이므로 실패해도 예외를 전파하지 않음
            // 로그만 남기고 메인 서비스는 정상 동작
            log.error("[랭킹 이벤트] 처리 실패 - postId: {}", event.getPostId(), e);
        }
    }
}
