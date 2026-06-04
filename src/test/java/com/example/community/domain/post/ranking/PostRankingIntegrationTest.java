package com.example.community.domain.post.ranking;

import com.example.community.domain.like.PostLike;
import com.example.community.domain.like.PostLikeRepository;
import com.example.community.domain.like.PostLikeService;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import com.example.community.domain.user.User;
import com.example.community.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TestContainers를 사용한 Redis 랭킹 통합 테스트
 *
 * 실제 Redis 컨테이너를 띄워서 이벤트 발행 → Sorted Set 업데이트를 검증합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("test")
class PostRankingIntegrationTest {

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(true);

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private PostLikeService postLikeService;

    @Autowired
    private PostRankingService rankingService;

    private User testUser;
    private Post testPost;

    @BeforeEach
    void setUp() {
        // 데이터 초기화
        postLikeRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();

        // 테스트 사용자 생성
        testUser = User.builder()
                .username("testuser")
                .email("test@example.com")
                .password("password")
                .build();
        userRepository.save(testUser);

        // 테스트 게시글 생성
        testPost = Post.builder()
                .title("테스트 게시글")
                .content("테스트 내용")
                .user(testUser)
                .viewCount(50) // 초기 조회수 50
                .build();
        postRepository.save(testPost);
    }

    @Test
    @DisplayName("좋아요 등록 시 Redis 랭킹 업데이트 검증")
    void testLikeEventUpdatesRanking() throws InterruptedException {
        // given
        Long postId = testPost.getId();

        // when: 좋아요 등록 (이벤트 발행)
        postLikeService.likePost(postId, testUser.getUsername());

        // 비동기 이벤트 처리 대기
        Thread.sleep(500);

        // then: Redis에 랭킹 데이터 확인
        Double score = rankingService.getScore(postId);
        assertThat(score).isNotNull();

        // 예상 점수: 좋아요 1개 * 10 + 조회수 50 * 1 = 60
        assertThat(score).isEqualTo(60.0);

        // Top 10에 포함되는지 확인
        Set<Long> topRanking = rankingService.getTopRanking(10);
        assertThat(topRanking).contains(postId);
    }

    @Test
    @DisplayName("좋아요 취소 시 Redis 랭킹 업데이트 검증")
    void testUnlikeEventUpdatesRanking() throws InterruptedException {
        // given: 좋아요가 이미 등록된 상태
        PostLike like = PostLike.builder()
                .user(testUser)
                .post(testPost)
                .build();
        postLikeRepository.save(like);

        // 초기 랭킹 등록 (좋아요 1개)
        rankingService.updateScore(testPost.getId(), 1 * 10.0 + 50 * 1.0);
        Thread.sleep(100);

        Double initialScore = rankingService.getScore(testPost.getId());
        assertThat(initialScore).isEqualTo(60.0);

        // when: 좋아요 취소 (이벤트 발행)
        postLikeService.unlikePost(testPost.getId(), testUser.getUsername());

        // 비동기 이벤트 처리 대기
        Thread.sleep(500);

        // then: Redis에서 점수 갱신 확인
        Double updatedScore = rankingService.getScore(testPost.getId());
        assertThat(updatedScore).isNotNull();

        // 예상 점수: 좋아요 0개 * 10 + 조회수 50 * 1 = 50
        assertThat(updatedScore).isEqualTo(50.0);
    }

    @Test
    @DisplayName("여러 게시글 랭킹 정렬 검증")
    void testMultiplePostsRanking() throws InterruptedException {
        // given: 점수가 다른 3개의 게시글 생성
        Post post1 = createPostWithScore("게시글1", 10, 100); // 점수: 10*10 + 100 = 200
        Post post2 = createPostWithScore("게시글2", 20, 50);  // 점수: 20*10 + 50 = 250
        Post post3 = createPostWithScore("게시글3", 5, 300);  // 점수: 5*10 + 300 = 350

        // when: Top 3 조회
        Set<ZSetOperations.TypedTuple<String>> topRanking =
                rankingService.getTopRankingWithScores(3);

        // then: 점수 높은 순으로 정렬 확인 (post3 > post2 > post1)
        assertThat(topRanking).hasSize(3);

        var rankingList = topRanking.stream().toList();
        assertThat(Long.parseLong(rankingList.get(0).getValue())).isEqualTo(post3.getId());
        assertThat(rankingList.get(0).getScore()).isEqualTo(350.0);

        assertThat(Long.parseLong(rankingList.get(1).getValue())).isEqualTo(post2.getId());
        assertThat(rankingList.get(1).getScore()).isEqualTo(250.0);

        assertThat(Long.parseLong(rankingList.get(2).getValue())).isEqualTo(post1.getId());
        assertThat(rankingList.get(2).getScore()).isEqualTo(200.0);
    }

    @Test
    @DisplayName("순위 조회 검증")
    void testGetRank() throws InterruptedException {
        // given
        Post highScorePost = createPostWithScore("인기글", 100, 1000); // 점수: 2000
        Post lowScorePost = createPostWithScore("비인기글", 1, 10);   // 점수: 20

        // when
        Long highRank = rankingService.getRank(highScorePost.getId());
        Long lowRank = rankingService.getRank(lowScorePost.getId());

        // then
        assertThat(highRank).isEqualTo(1L); // 1위
        assertThat(lowRank).isEqualTo(2L);  // 2위
    }

    /**
     * 헬퍼 메서드: 점수가 있는 게시글 생성 및 랭킹 등록
     */
    private Post createPostWithScore(String title, int likeCount, long viewCount) {
        Post post = Post.builder()
                .title(title)
                .content("내용")
                .user(testUser)
                .viewCount(viewCount)
                .build();
        postRepository.save(post);

        // 좋아요 데이터 생성
        for (int i = 0; i < likeCount; i++) {
            PostLike like = PostLike.builder()
                    .user(testUser)
                    .post(post)
                    .build();
            try {
                postLikeRepository.save(like);
            } catch (Exception e) {
                // 중복 무시
            }
        }

        // Redis 랭킹 등록
        double score = likeCount * 10.0 + viewCount * 1.0;
        rankingService.updateScore(post.getId(), score);

        return post;
    }
}
