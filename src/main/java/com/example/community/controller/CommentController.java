package com.example.community.controller;

import com.example.community.domain.comment.Comment;
import com.example.community.domain.comment.CommentRepository;
import com.example.community.domain.comment.CommentService;
import com.example.community.domain.comment.dto.CommentResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;
    private final CommentRepository commentRepository;

    @PostMapping
    public Comment create(
            @RequestParam Long postId,
            @RequestParam String content
    ){
        return commentService.create(postId,content);
    }

    @GetMapping("/post/{postId}")
    public List<CommentResponseDto> list(@PathVariable Long postId){
        return commentService.findByPost(postId);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id){
        commentService.delete(id);
    }
}