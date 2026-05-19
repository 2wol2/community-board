package com.example.community.controller;

import com.example.community.domain.like.PostLikeService;
import com.example.community.domain.post.PostService;
import com.example.community.domain.post.dto.PostListDto;
import com.example.community.global.jwt.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Import(ControllerTestConfig.class)
class PostControllerTest {

    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper objectMapper;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @MockitoBean PostService postService;
    @MockitoBean PostLikeService postLikeService;
    @MockitoBean JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("GET /api/posts - JWT 인증 성공 200")
    @WithMockUser
    void list_authenticated() throws Exception {
        given(postService.findAll()).willReturn(List.of(
                new PostListDto(1L, "제목1", "내용1"),
                new PostListDto(2L, "제목2", "내용2")
        ));

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("제목1"));
    }

    @Test
    @DisplayName("GET /api/posts - JWT 없음 401")
    void list_unauthorized() throws Exception {
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/posts - JWT 인증 + 게시글 생성 200")
    @WithMockUser(username = "user1")
    void create_success() throws Exception {
        given(postService.create(eq("user1"), any(), any()))
                .willReturn(new PostListDto(1L, "새 게시글", "내용"));

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "새 게시글",
                                "content", "내용"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("새 게시글"));
    }

    @Test
    @DisplayName("POST /api/posts - Validation 실패 400")
    @WithMockUser
    void create_validationFail() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "",    // @NotBlank 위반
                                "content", ""   // @NotBlank 위반
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/posts - JWT 없음 401")
    void create_unauthorized() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "제목",
                                "content", "내용"
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/posts/{id} - 게시글 수정 200")
    @WithMockUser
    void update_success() throws Exception {
        given(postService.update(eq(1L), any(), any()))
                .willReturn(new PostListDto(1L, "수정된 제목", "수정된 내용"));

        mockMvc.perform(put("/api/posts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "수정된 제목",
                                "content", "수정된 내용"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 제목"));
    }

    @Test
    @DisplayName("DELETE /api/posts/{id} - 게시글 삭제 200")
    @WithMockUser
    void delete_success() throws Exception {
        mockMvc.perform(delete("/api/posts/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/posts/{id}/like - 좋아요 200")
    @WithMockUser(username = "user1")
    void like_success() throws Exception {
        mockMvc.perform(post("/api/posts/1/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("좋아요 성공"));
    }

    @Test
    @DisplayName("DELETE /api/posts/{id}/like - 좋아요 취소 200")
    @WithMockUser(username = "user1")
    void unlike_success() throws Exception {
        mockMvc.perform(delete("/api/posts/1/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("좋아요 취소 성공"));
    }

    @Test
    @DisplayName("GET /api/posts/{id}/likes - 좋아요 수 조회 200")
    @WithMockUser
    void likeCount_success() throws Exception {
        given(postLikeService.countLikes(1L)).willReturn(5L);

        mockMvc.perform(get("/api/posts/1/likes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(5));
    }
}
