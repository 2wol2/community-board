package com.example.community.domain.like;

import com.example.community.domain.post.Post;
import com.example.community.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    boolean existsByUserAndPost(User user, Post post);

    void deleteByUserAndPost(User user, Post post);

    long countByPostId(Long postId);

    /**
     * 여러 게시글의 좋아요 수를 한 번에 조회 (N+1 방지)
     *
     * @param postIds 게시글 ID 목록
     * @return Map<postId, likeCount>
     */
    @Query("SELECT pl.post.id AS postId, COUNT(pl) AS likeCount " +
           "FROM PostLike pl " +
           "WHERE pl.post.id IN :postIds " +
           "GROUP BY pl.post.id")
    List<LikeCountProjection> countLikesByPostIds(@Param("postIds") List<Long> postIds);

    /**
     * 좋아요 수 조회 결과 Projection
     */
    interface LikeCountProjection {
        Long getPostId();
        Long getLikeCount();
    }

    /**
     * Projection을 Map으로 변환하는 유틸리티 메서드
     */
    default Map<Long, Long> countLikesByPostIdsAsMap(List<Long> postIds) {
        return countLikesByPostIds(postIds).stream()
                .collect(Collectors.toMap(
                        LikeCountProjection::getPostId,
                        LikeCountProjection::getLikeCount
                ));
    }
}
