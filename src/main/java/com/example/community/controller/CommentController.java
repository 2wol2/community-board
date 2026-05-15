package com.example.community.controller;

import com.example.community.domain.comment.CommentService;
import com.example.community.domain.comment.dto.CommentRequestDto;
import com.example.community.domain.comment.dto.CommentResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    public CommentResponseDto create(@RequestBody @Valid CommentRequestDto request){
        return commentService.create(request.getPostId(), request.getContent());
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