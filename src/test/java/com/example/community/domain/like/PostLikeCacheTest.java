package com.example.community.domain.like;

import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import com.example.community.domain.user.User;
import com.example.community.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostLikeCacheTest {

    @Autowired
    private PostLikeService postLikeService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @MockitoBean
    private PostLikeRepository postLikeRepository;

    @Autowired
    private CacheManager cacheManager;

    private User user;
    private Post post;

    @BeforeEach
    void setUp() {
        // 캐시 초기화
        cacheManager.getCacheNames().forEach(cacheName ->
                cacheManager.getCache(cacheName).clear()
        );

        // 테스트 데이터 생성
        user = User.builder()
                .username("cacheTestUser")
                .password("password")
                .email("cache@test.com")
                .build();
        userRepository.save(user);

        post = Post.builder()
                .title("Cache Test Post")
                .content("Testing cache")
                .user(user)
                .build();
        postRepository.save(post);
    }

    @Test
    @DisplayName("캐시 히트 시 DB 쿼리가 발생하지 않음")
    void countLikes_cacheHit_noDatabaseQuery() {
        Long postId = post.getId();

        // Mock 설정: countByPostId가 0을 반환하도록
        when(postLikeRepository.countByPostId(postId)).thenReturn(0L);

        // 첫 번째 호출 → DB 조회 (캐시 miss)
        long firstCount = postLikeService.countLikes(postId);

        // 두 번째 호출 → 캐시에서 조회 (캐시 hit, DB 쿼리 없음)
        long secondCount = postLikeService.countLikes(postId);

        // 검증: DB가 딱 1번만 호출됐는지 확인
        verify(postLikeRepository, times(1)).countByPostId(postId);
        assertThat(firstCount).isEqualTo(secondCount).isZero();
    }

    @Test
    @DisplayName("좋아요 등록 후 캐시가 삭제됨")
    void likePost_evictsCache() {
        Long postId = post.getId();

        // Mock 설정
        when(postLikeRepository.countByPostId(postId)).thenReturn(0L, 1L);
        when(postLikeRepository.existsByUserAndPost(any(), any())).thenReturn(false);

        // 1. 좋아요 수 조회 (캐시 저장)
        postLikeService.countLikes(postId);
        verify(postLikeRepository, times(1)).countByPostId(postId);

        // 2. 좋아요 등록 (캐시 삭제)
        postLikeService.likePost(postId, user.getUsername());

        // 3. 다시 좋아요 수 조회 (캐시 miss, DB 조회)
        long count = postLikeService.countLikes(postId);

        // 검증: DB가 3번 호출됐는지 확인 (초기 1번 + likePost 이벤트 발행 1번 + 캐시 삭제 후 1번)
        verify(postLikeRepository, times(3)).countByPostId(postId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("좋아요 취소 후 캐시가 삭제됨")
    void unlikePost_evictsCache() {
        Long postId = post.getId();

        // Mock 설정
        when(postLikeRepository.existsByUserAndPost(any(), any())).thenReturn(false, true);
        when(postLikeRepository.countByPostId(postId)).thenReturn(1L, 0L);

        // 1. 좋아요 등록
        postLikeService.likePost(postId, user.getUsername());

        // 2. 좋아요 수 조회 (캐시 저장)
        postLikeService.countLikes(postId);
        verify(postLikeRepository, times(1)).countByPostId(postId);

        // 3. 좋아요 취소 (캐시 삭제)
        postLikeService.unlikePost(postId, user.getUsername());

        // 4. 다시 좋아요 수 조회 (캐시 miss, DB 조회)
        long count = postLikeService.countLikes(postId);

        // 검증: DB가 2번 호출됐는지 확인 (초기 1번 + 캐시 삭제 후 1번)
        verify(postLikeRepository, times(2)).countByPostId(postId);
        assertThat(count).isZero();
    }
}
