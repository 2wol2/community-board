package com.example.community.domain.like;

import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import com.example.community.domain.user.User;
import com.example.community.domain.user.UserRepository;
import com.example.community.global.exception.CustomException;
import com.example.community.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostLikeService {
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final UserRepository userRepository;

    @Transactional
    public void likePost(Long postId, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        // 중복 체크 (빠른 실패)
        if (postLikeRepository.existsByUserAndPost(user, post)) {
            throw new CustomException(ErrorCode.ALREADY_LIKED);
        }

        PostLike like = PostLike.builder()
                .user(user)
                .post(post)
                .build();

        try {
            postLikeRepository.save(like);
        } catch (DataIntegrityViolationException e) {
            // 유니크 제약 조건 위반 (동시성 문제로 중복 저장 시도)
            log.warn("중복 좋아요 시도 감지 - username: {}, postId: {}", username, postId);
            throw new CustomException(ErrorCode.ALREADY_LIKED);
        }
    }

    @Transactional
    public void unlikePost(Long postId, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        if (!postLikeRepository.existsByUserAndPost(user, post)) {
            throw new CustomException(ErrorCode.LIKE_NOT_FOUND);
        }

        postLikeRepository.deleteByUserAndPost(user, post);
    }

    public long countLikes(Long postId){
        return postLikeRepository.countByPostId(postId);
    }
}
