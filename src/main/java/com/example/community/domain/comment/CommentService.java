package com.example.community.domain.comment;

import com.example.community.domain.comment.dto.CommentResponseDto;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;

    public Comment create(Long postId, String content) {

        Post post = postRepository.findById(postId)
                .orElseThrow();

        Comment comment = Comment.builder()
                .content(content)
                .post(post)
                .build();

        return commentRepository.save(comment);
    }

    public List<CommentResponseDto> findByPost(Long postId) {
        return commentRepository.findByPostId(postId)
                .stream()
                .map(comment -> new CommentResponseDto(comment.getId(), comment.getContent()))
                .toList();
    }

    public void delete(Long commentId){
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow();

        commentRepository.delete(comment);
    }

}