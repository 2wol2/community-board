package com.example.community.domain.post;

import com.example.community.domain.post.dto.PostListDto;
import com.example.community.domain.post.dto.PostResponseDto;
import com.example.community.domain.user.UserRepository;
import com.example.community.global.exception.CustomException;
import com.example.community.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.example.community.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock PostRepository postRepository;
    @Mock UserRepository userRepository;
    @InjectMocks PostService postService;

    @Test
    @DisplayName("게시글 생성 성공")
    void create_success() {
        given(userRepository.findByUsername("user1")).willReturn(Optional.of(createUser()));
        given(postRepository.save(any())).willReturn(createPost());

        PostListDto result = postService.create("user1", "제목", "내용");

        assertThat(result.getTitle()).isEqualTo("제목");
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 게시글 생성 시 USER_NOT_FOUND 예외")
    void create_userNotFound() {
        given(userRepository.findByUsername("noone")).willReturn(Optional.empty());

        CustomException ex = catchThrowableOfType(
                () -> postService.create("noone", "제목", "내용"),
                CustomException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("게시글 수정 시 제목과 내용이 변경됨")
    void update_success() {
        Post post = createPost();
        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(postRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        PostListDto result = postService.update(1L, "새 제목", "새 내용");

        assertThat(post.getTitle()).isEqualTo("새 제목");
        assertThat(post.getContent()).isEqualTo("새 내용");
        assertThat(result.getTitle()).isEqualTo("새 제목");
    }

    @Test
    @DisplayName("게시글 삭제 성공")
    void delete_success() {
        Post post = createPost();
        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        postService.delete(1L);

        verify(postRepository).delete(post);
    }

    @Test
    @DisplayName("존재하지 않는 게시글 조회 시 POST_NOT_FOUND 예외")
    void findById_notFound() {
        given(postRepository.findById(99L)).willReturn(Optional.empty());

        CustomException ex = catchThrowableOfType(
                () -> postService.findById(99L),
                CustomException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("게시글 상세 조회 시 조회수 증가")
    void findDetail_increasesViewCount() {
        Post post = Post.builder()
                .id(1L).title("제목").content("내용")
                .comments(List.of()).viewCount(0L)
                .build();
        given(postRepository.findPostWithComments(1L)).willReturn(post);

        PostResponseDto result = postService.findDetail(1L);

        assertThat(post.getViewCount()).isEqualTo(1L);
        assertThat(result.getViewCount()).isEqualTo(1L);
    }
}
