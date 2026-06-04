package com.example.community.domain.post.scheduler;

import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import com.example.community.domain.post.RecruitStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 모집 마감 자동화 스케줄러
 *
 * 매일 자정에 deadline이 지난 스터디를 자동으로 CLOSED 처리합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecruitScheduler {

    private final PostRepository postRepository;

    /**
     * 매일 자정에 실행 (Cron: 초 분 시 일 월 요일)
     * 0 0 0 * * * = 매일 00:00:00
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void closeExpiredRecruits() {
        LocalDateTime now = LocalDateTime.now();

        // deadline이 현재 시간보다 이전이고, 아직 모집 중인 스터디 조회
        List<Post> expiredPosts = postRepository.findExpiredRecruits(now, RecruitStatus.RECRUITING);

        if (expiredPosts.isEmpty()) {
            log.info("[스케줄러] 마감할 스터디 없음");
            return;
        }

        // 모집 마감 처리
        expiredPosts.forEach(Post::closeRecruit);

        log.info("[스케줄러] 모집 마감 처리 완료 - 대상: {}개", expiredPosts.size());
    }

}
