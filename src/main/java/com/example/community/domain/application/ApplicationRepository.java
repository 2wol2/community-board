package com.example.community.domain.application;

import com.example.community.domain.post.Post;
import com.example.community.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    /**
     * 특정 게시글의 모든 지원 조회
     */
    List<Application> findByPost(Post post);

    /**
     * 특정 게시글의 특정 상태 지원 조회
     */
    List<Application> findByPostAndStatus(Post post, ApplicationStatus status);

    /**
     * 특정 사용자의 모든 지원 조회
     */
    List<Application> findByUser(User user);

    /**
     * 특정 사용자가 특정 게시글에 지원했는지 확인
     */
    boolean existsByUserAndPost(User user, Post post);

    /**
     * 특정 사용자의 특정 게시글 지원 조회
     */
    Optional<Application> findByUserAndPost(User user, Post post);

    /**
     * 특정 게시글의 수락된 지원 수 조회
     */
    @Query("SELECT COUNT(a) FROM Application a WHERE a.post = :post AND a.status = 'ACCEPTED'")
    Long countAcceptedByPost(@Param("post") Post post);

}
