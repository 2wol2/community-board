package com.example.community.domain.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

}