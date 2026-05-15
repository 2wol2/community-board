package com.example.community.domain.comment;

import com.example.community.domain.comment.dto.CommentResponseDto;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import com.example.community.global.exception.CustomException;
import com.example.community.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;

    public CommentResponseDto create(Long postId, String content) {

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        Comment comment = Comment.builder()
                .content(content)
                .post(post)
                .build();

        Comment saved = commentRepository.save(comment);
        return new CommentResponseDto(saved.getId(), saved.getContent());
    }

    public List<CommentResponseDto> findByPost(Long postId) {
        return commentRepository.findByPostId(postId)
                .stream()
                .map(comment -> new CommentResponseDto(comment.getId(), comment.getContent()))
                .toList();
    }

    public void delete(Long commentId){
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));

        commentRepository.delete(comment);
    }

}