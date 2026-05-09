package com.example.community;

import com.example.community.domain.comment.Comment;
import com.example.community.domain.comment.CommentRepository;
import com.example.community.domain.like.PostLike;
import com.example.community.domain.like.PostLikeRepository;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import com.example.community.domain.post.dto.PostListDto;
import com.example.community.domain.user.User;
import com.example.community.domain.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class QueryPerformanceTest {

    static final int N = 10;
    static final int RUNS = 3; // 워밍업 1회 + 측정 3회 평균

    @Autowired PostRepository postRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired PostLikeRepository postLikeRepository;
    @Autowired UserRepository userRepository;

    @PersistenceContext EntityManager em;

    Statistics stats;
    List<Post> savedPosts = new ArrayList<>();

    record Result(long queries, long ms) {}

    @BeforeEach
    void setUp() {
        stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);

        for (int i = 0; i < N; i++) {
            User user = userRepository.save(User.builder()
                    .username("perfUser" + i)
                    .email("perf" + i + "@test.com")
                    .password("pw")
                    .build());
            Post post = postRepository.save(Post.builder()
                    .title("Title " + i)
                    .content("Content " + i)
                    .user(user)
                    .viewCount(0L)
                    .build());
            commentRepository.save(Comment.builder().content("Comment A").post(post).build());
            commentRepository.save(Comment.builder().content("Comment B").post(post).build());
            postLikeRepository.save(PostLike.builder().user(user).post(post).build());
            savedPosts.add(post);
        }
        em.flush();
        em.clear();
    }

    @AfterEach
    void tearDown() {
        savedPosts.clear();
    }

    private Result measure(Runnable action) {
        // 워밍업 1회
        action.run();
        em.clear();

        // 측정 RUNS회 평균
        long totalMs = 0;
        long queryCount = 0;
        for (int i = 0; i < RUNS; i++) {
            stats.clear();
            long start = System.nanoTime();
            action.run();
            totalMs += (System.nanoTime() - start) / 1_000_000;
            queryCount = stats.getPrepareStatementCount();
            em.clear();
        }
        return new Result(queryCount, totalMs / RUNS);
    }

    private void printResult(String scenario, Result before, Result after) {
        long savedQueries = before.queries() - after.queries();
        long savedMs = before.ms() - after.ms();
        double queryRate = (1.0 - (double) after.queries() / before.queries()) * 100;
        double msRate = before.ms() > 0 ? (1.0 - (double) after.ms() / before.ms()) * 100 : 0;

        System.out.printf("%n┌─────────────────────────────────────────────────────┐%n");
        System.out.printf("│  %-51s│%n", scenario);
        System.out.printf("├───────────────┬─────────────────┬────────────────────┤%n");
        System.out.printf("│               │    쿼리 수      │    응답 시간       │%n");
        System.out.printf("├───────────────┼─────────────────┼────────────────────┤%n");
        System.out.printf("│  수정 전      │    %3d 쿼리     │    %6d ms       │%n", before.queries(), before.ms());
        System.out.printf("│  수정 후      │    %3d 쿼리     │    %6d ms       │%n", after.queries(), after.ms());
        System.out.printf("├───────────────┼─────────────────┼────────────────────┤%n");
        System.out.printf("│  절감         │  %3d↓ (%.0f%%)  │  %4d ms↓ (%.0f%%)  │%n",
                savedQueries, queryRate, savedMs, msRate);
        System.out.printf("└───────────────┴─────────────────┴────────────────────┘%n");
    }

    @Test
    @DisplayName("게시글 목록 조회 - N+1 vs LAZY")
    void postList() {
        Result before = measure(() -> {
            List<Post> posts = postRepository.findAll();
            posts.forEach(p -> Hibernate.initialize(p.getUser())); // EAGER 시뮬레이션
        });

        Result after = measure(() ->
            postRepository.findAll()
                    .stream()
                    .map(p -> new PostListDto(p.getId(), p.getTitle(), p.getContent()))
                    .toList()
        );

        printResult("게시글 목록 조회  (N=" + N + ")", before, after);
        assertThat(after.queries()).isEqualTo(1);
        assertThat(before.queries()).isGreaterThanOrEqualTo(N + 1);
    }

    @Test
    @DisplayName("게시글 페이징 조회 - N+1 vs LAZY")
    void postPaged() {
        Result before = measure(() -> {
            var page = postRepository.findAll(PageRequest.of(0, N));
            page.forEach(p -> Hibernate.initialize(p.getUser()));
        });

        Result after = measure(() ->
            postRepository.findAll(PageRequest.of(0, N))
                    .map(p -> new PostListDto(p.getId(), p.getTitle(), p.getContent()))
        );

        printResult("게시글 페이징 조회 (N=" + N + ")", before, after);
        assertThat(after.queries()).isEqualTo(2); // content + count
        assertThat(before.queries()).isGreaterThanOrEqualTo(N + 2);
    }

    @Test
    @DisplayName("게시글 상세 조회 - 별도 조회 vs JOIN FETCH")
    void postDetail() {
        Long postId = savedPosts.get(0).getId();

        Result before = measure(() -> {
            Post post = postRepository.findById(postId).orElseThrow();
            Hibernate.initialize(post.getComments());
        });

        Result after = measure(() -> postRepository.findPostWithComments(postId));

        printResult("게시글 상세 조회 (댓글 2개)", before, after);
        assertThat(after.queries()).isEqualTo(1);
        assertThat(before.queries()).isEqualTo(2);
    }

    @Test
    @DisplayName("댓글 목록 조회 - post 사전 조회 vs 직접 조회")
    void commentList() {
        Long postId = savedPosts.get(0).getId();

        Result before = measure(() -> {
            Post post = postRepository.findById(postId).orElseThrow();
            commentRepository.findByPostId(post.getId());
        });

        Result after = measure(() -> commentRepository.findByPostId(postId));

        printResult("댓글 목록 조회", before, after);
        assertThat(after.queries()).isEqualTo(1);
        assertThat(before.queries()).isEqualTo(2);
    }

    @Test
    @DisplayName("좋아요 수 조회 - post 사전 조회 vs 직접 count")
    void likeCount() {
        Long postId = savedPosts.get(0).getId();

        Result before = measure(() -> {
            Post post = postRepository.findById(postId).orElseThrow();
            postLikeRepository.countByPostId(post.getId());
        });

        Result after = measure(() -> postLikeRepository.countByPostId(postId));

        printResult("좋아요 수 조회", before, after);
        assertThat(after.queries()).isEqualTo(1);
        assertThat(before.queries()).isEqualTo(2);
    }
}
