package com.example.community.domain.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    @Query("""
    SELECT p FROM Post p
    LEFT JOIN FETCH p.comments
    WHERE p.id = :id
    """)
    Post findPostWithComments(Long id);

    @Query("""
    select p from Post p
    where p.title like %:keyword%
       or p.content like %:keyword%
    """)
    Page<Post> search(String keyword, Pageable pageable);

    /**
     * 스터디 검색 (제목/내용 + 카테고리 + 모집 상태 필터)
     */
    @Query("""
    SELECT p FROM Post p
    WHERE (:keyword IS NULL OR p.title LIKE %:keyword% OR p.content LIKE %:keyword%)
      AND (:category IS NULL OR p.category = :category)
      AND (:status IS NULL OR p.status = :status)
    ORDER BY p.createdAt DESC
    """)
    Page<Post> searchStudies(String keyword, Category category, RecruitStatus status, Pageable pageable);

    /**
     * 특정 사용자가 작성한 모집글 조회
     */
    @Query("""
    SELECT p FROM Post p
    WHERE p.user.id = :userId
    ORDER BY p.createdAt DESC
    """)
    Page<Post> findByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * 마감일이 지난 모집 중인 스터디 조회 (스케줄러용)
     */
    @Query("""
    SELECT p FROM Post p
    WHERE p.deadline < :now
      AND p.status = :status
    """)
    List<Post> findExpiredRecruits(@Param("now") LocalDateTime now, @Param("status") RecruitStatus status);

}
