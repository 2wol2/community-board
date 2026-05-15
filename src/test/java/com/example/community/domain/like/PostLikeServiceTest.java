package com.example.community.domain.like;

import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import com.example.community.domain.user.User;
import com.example.community.domain.user.UserRepository;
import com.example.community.global.exception.CustomException;
import com.example.community.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.example.community.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostLikeServiceTest {

    @Mock PostRepository postRepository;
    @Mock PostLikeRepository postLikeRepository;
    @Mock UserRepository userRepository;
    @InjectMocks PostLikeService postLikeService;

    @Test
    @DisplayName("좋아요 성공")
    void likePost_success() {
        User user = createUser();
        Post post = createPost();
        given(userRepository.findByUsername("user1")).willReturn(Optional.of(user));
        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(postLikeRepository.existsByUserAndPost(user, post)).willReturn(false);

        postLikeService.likePost(1L, "user1");

        verify(postLikeRepository).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 사용자가 좋아요 시 USER_NOT_FOUND 예외")
    void likePost_userNotFound() {
        given(userRepository.findByUsername("noone")).willReturn(Optional.empty());

        CustomException ex = catchThrowableOfType(
                () -> postLikeService.likePost(1L, "noone"),
                CustomException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
        verify(postLikeRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 게시글에 좋아요 시 POST_NOT_FOUND 예외")
    void likePost_postNotFound() {
        given(userRepository.findByUsername("user1")).willReturn(Optional.of(createUser()));
        given(postRepository.findById(99L)).willReturn(Optional.empty());

        CustomException ex = catchThrowableOfType(
                () -> postLikeService.likePost(99L, "user1"),
                CustomException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.POST_NOT_FOUND);
        verify(postLikeRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 좋아요한 게시글에 좋아요 시 ALREADY_LIKED 예외")
    void likePost_alreadyLiked() {
        User user = createUser();
        Post post = createPost();
        given(userRepository.findByUsername("user1")).willReturn(Optional.of(user));
        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(postLikeRepository.existsByUserAndPost(user, post)).willReturn(true);

        CustomException ex = catchThrowableOfType(
                () -> postLikeService.likePost(1L, "user1"),
                CustomException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ALREADY_LIKED);
        verify(postLikeRepository, never()).save(any());
    }
}
