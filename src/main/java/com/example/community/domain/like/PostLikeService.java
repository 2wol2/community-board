package com.example.community.domain.like;

import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import com.example.community.domain.post.event.PostRankingEvent;
import com.example.community.domain.user.User;
import com.example.community.domain.user.UserRepository;
import com.example.community.global.config.CacheConfig;
import com.example.community.global.exception.CustomException;
import com.example.community.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostLikeService {
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @CacheEvict(value = CacheConfig.LIKE_COUNT_CACHE, key = "#postId", beforeInvocation = false)
    public void likePost(Long postId, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        // 중복 체크 (빠른 실패)
        if (postLikeRepository.existsByUserAndPost(user, post)) {
            throw new CustomException(ErrorCode.ALREADY_LIKED);
        }

        PostLike like = PostLike.builder()
                .user(user)
                .post(post)
                .build();

        try {
            postLikeRepository.save(like);

            // 좋아요 등록 성공 후 랭킹 업데이트 이벤트 발행
            publishRankingEvent(postId);

        } catch (DataIntegrityViolationException e) {
            // 유니크 제약 조건 위반 (동시성 문제로 중복 저장 시도)
            log.warn("중복 좋아요 시도 감지 - username: {}, postId: {}", username, postId);
            throw new CustomException(ErrorCode.ALREADY_LIKED);
        }
    }

    @Transactional
    @CacheEvict(value = CacheConfig.LIKE_COUNT_CACHE, key = "#postId", beforeInvocation = false)
    public void unlikePost(Long postId, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        if (!postLikeRepository.existsByUserAndPost(user, post)) {
            throw new CustomException(ErrorCode.LIKE_NOT_FOUND);
        }

        postLikeRepository.deleteByUserAndPost(user, post);

        // 좋아요 취소 후 랭킹 업데이트 이벤트 발행
        publishRankingEvent(postId);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.LIKE_COUNT_CACHE, key = "#postId")
    public long countLikes(Long postId){
        return postLikeRepository.countByPostId(postId);
    }

    /**
     * 랭킹 업데이트 이벤트 발행
     *
     * 좋아요 등록/취소 후 최신 좋아요 수와 조회수를 조회하여
     * 이벤트를 발행합니다. 이벤트는 @Async로 비동기 처리됩니다.
     *
     * @param postId 게시글 ID
     */
    private void publishRankingEvent(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        long likeCount = postLikeRepository.countByPostId(postId);
        long viewCount = post.getViewCount();

        PostRankingEvent event = new PostRankingEvent(postId, likeCount, viewCount);
        eventPublisher.publishEvent(event);

        log.debug("[이벤트 발행] postId: {}, likeCount: {}, viewCount: {}", postId, likeCount, viewCount);
    }
}
