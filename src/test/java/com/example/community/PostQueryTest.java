package com.example.community;

import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import com.example.community.domain.post.dto.PostListDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@SpringBootTest
@Transactional
class PostQueryTest {

    @Autowired
    private PostRepository postRepository;

    @PersistenceContext
    private EntityManager em;

    private Statistics stats;

    @BeforeEach
    void setUp() {
        stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
    }

    @ParameterizedTest(name = "게시글 {0}개 - N+1 vs LAZY 비교")
    @ValueSource(ints = {10, 100, 1000})
    void 게시글_목록_조회_쿼리수_비교(int limit) {
        // ── 수정 전: 각 post의 user를 개별 조회 (EAGER 시뮬레이션) ──
        em.clear();
        stats.clear();
        long beforeStart = System.currentTimeMillis();
        List<Post> posts = postRepository.findAll(PageRequest.of(0, limit)).getContent();
        posts.forEach(p -> Hibernate.initialize(p.getUser())); // N+1 유발
        long beforeMs = System.currentTimeMillis() - beforeStart;
        long beforeQueries = stats.getPrepareStatementCount();

        // ── 수정 후: LAZY, user 미접근 ──
        em.clear();
        stats.clear();
        long afterStart = System.currentTimeMillis();
        List<PostListDto> dtos = postRepository.findAll(PageRequest.of(0, limit))
                .map(p -> new PostListDto(p.getId(), p.getTitle(), p.getContent()))
                .getContent();
        long afterMs = System.currentTimeMillis() - afterStart;
        long afterQueries = stats.getPrepareStatementCount();

        System.out.printf("%n[N=%-4d] 수정 전: %4d 쿼리 / %4dms | 수정 후: %2d 쿼리 / %4dms | 쿼리 %.0f%% 감소%n",
                limit,
                beforeQueries, beforeMs,
                afterQueries, afterMs,
                (1.0 - (double) afterQueries / beforeQueries) * 100);
    }
}
