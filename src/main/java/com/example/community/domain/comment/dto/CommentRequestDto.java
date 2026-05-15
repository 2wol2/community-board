package com.example.community.domain.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class CommentRequestDto {

    @NotNull(message = "게시글 ID는 필수입니다.")
    private Long postId;

    @NotBlank(message = "내용은 필수입니다.")
    @Size(max = 500, message = "댓글은 500자 이하여야 합니다.")
    private String content;
}
