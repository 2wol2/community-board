package com.example.community.controller;

import com.example.community.domain.like.PostLikeService;
import com.example.community.domain.post.PostService;
import com.example.community.domain.post.dto.PostListDto;
import com.example.community.domain.post.dto.PostRequestDto;
import com.example.community.domain.post.dto.PostResponseDto;
import com.example.community.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;
    private final PostLikeService postLikeService;

    @PostMapping
    public PostListDto create(
            @RequestBody @Valid PostRequestDto request,
            Authentication authentication
    ){
        return postService.create(authentication.getName(), request.getTitle(), request.getContent());
    }

    @GetMapping
    public List<PostListDto> list(){
        return postService.findAll();
    }

    @GetMapping("/{id}")
    public PostResponseDto detail(@PathVariable Long id){
        return postService.findDetail(id);
    }

    @GetMapping("/paged")
    public Page<PostListDto> listPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ){
        return postService.findAllPaged(page, size);
    }

    @GetMapping("/search")
    public Page<PostListDto> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ){
        return postService.search(keyword, page, size);
    }

    @PutMapping("/{id}")
    public PostListDto update(
            @PathVariable Long id,
            @RequestBody @Valid PostRequestDto request
    ){
        return postService.update(id, request.getTitle(), request.getContent());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id){
        postService.delete(id);
    }

    @PostMapping("/{id}/like")
    public ApiResponse<?> likePost(
            @PathVariable Long id,
            Authentication authentication
    ){
        String username = authentication.getName();
        postLikeService.likePost(id, username);
        return ApiResponse.success("좋아요 성공");
    }

    @GetMapping("/{id}/likes")
    public ApiResponse<Long> likeCount(@PathVariable Long id){

        long count = postLikeService.countLikes(id);

        return ApiResponse.success(count);
    }
}