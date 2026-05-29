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
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PostLikeConcurrencyTest {

    @Autowired
    private PostLikeService postLikeService;

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private Post testPost;

    @BeforeEach
    void setUp() {
        // 기존 데이터 정리
        postLikeRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();

        // 테스트 데이터 생성
        testUser = User.builder()
                .username("concurrency_test_user")
                .email("concurrency@test.com")
                .password("password123")
                .build();
        userRepository.save(testUser);

        testPost = Post.builder()
                .title("Concurrency Test Post")
                .content("Test Content")
                .user(testUser)
                .build();
        postRepository.save(testPost);
    }

    @Test
    @DisplayName("100개 동시 좋아요 요청 시 1개만 저장되어야 함")
    void concurrentLikeRequests_shouldSaveOnlyOne() throws InterruptedException {
        // Given
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 100개의 스레드가 동시에 같은 게시글에 좋아요
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    postLikeService.likePost(testPost.getId(), testUser.getUsername());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        // Then: 실제로 저장된 좋아요 개수 확인
        long actualLikeCount = postLikeRepository.countByPostId(testPost.getId());

        System.out.println("\n=== 동시성 테스트 결과 ===");
        System.out.println("총 요청 수: " + threadCount);
        System.out.println("성공 처리: " + successCount.get());
        System.out.println("실패 처리 (ALREADY_LIKED): " + failCount.get());
        System.out.println("실제 DB 저장된 좋아요: " + actualLikeCount + "개");
        System.out.println("======================\n");

        // 검증: 정확히 1개만 저장되어야 함
        assertThat(actualLikeCount).isEqualTo(1);
        // 성공은 최소 1개 이상 (동시성 환경에서 정확히 1이 아닐 수 있음)
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("같은 유저가 순차적으로 좋아요 → 취소 → 다시 좋아요")
    void likeUnlikeLike_shouldWork() {
        // When & Then
        postLikeService.likePost(testPost.getId(), testUser.getUsername());
        assertThat(postLikeRepository.countByPostId(testPost.getId())).isEqualTo(1);

        postLikeService.unlikePost(testPost.getId(), testUser.getUsername());
        assertThat(postLikeRepository.countByPostId(testPost.getId())).isEqualTo(0);

        postLikeService.likePost(testPost.getId(), testUser.getUsername());
        assertThat(postLikeRepository.countByPostId(testPost.getId())).isEqualTo(1);
    }
}
