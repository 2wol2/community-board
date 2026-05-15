package com.example.community.domain.comment;

import com.example.community.domain.comment.dto.CommentResponseDto;
import com.example.community.domain.post.PostRepository;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock CommentRepository commentRepository;
    @Mock PostRepository postRepository;
    @InjectMocks CommentService commentService;

    @Test
    @DisplayName("댓글 생성 성공")
    void create_success() {
        given(postRepository.findById(1L)).willReturn(Optional.of(createPost()));
        given(commentRepository.save(any())).willReturn(createComment());

        CommentResponseDto result = commentService.create(1L, "댓글 내용");

        assertThat(result.content()).isEqualTo("댓글 내용");
    }

    @Test
    @DisplayName("존재하지 않는 게시글에 댓글 생성 시 POST_NOT_FOUND 예외")
    void create_postNotFound() {
        given(postRepository.findById(99L)).willReturn(Optional.empty());

        CustomException ex = catchThrowableOfType(
                () -> commentService.create(99L, "댓글 내용"),
                CustomException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("댓글 삭제 성공")
    void delete_success() {
        Comment comment = createComment();
        given(commentRepository.findById(1L)).willReturn(Optional.of(comment));

        commentService.delete(1L);

        verify(commentRepository).delete(comment);
    }

    @Test
    @DisplayName("존재하지 않는 댓글 삭제 시 COMMENT_NOT_FOUND 예외")
    void delete_commentNotFound() {
        given(commentRepository.findById(99L)).willReturn(Optional.empty());

        CustomException ex = catchThrowableOfType(
                () -> commentService.delete(99L),
                CustomException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
    }
}
