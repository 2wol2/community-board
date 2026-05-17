package com.example.community.domain.like;

import com.example.community.domain.post.Post;
import com.example.community.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;


public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    boolean existsByUserAndPost(User user, Post post);

    void deleteByUserAndPost(User user, Post post);

    long countByPostId(Long postId);
}
