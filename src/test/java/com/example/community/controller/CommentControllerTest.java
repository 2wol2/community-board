package com.example.community.controller;

import com.example.community.domain.comment.CommentService;
import com.example.community.domain.comment.dto.CommentResponseDto;
import com.example.community.global.jwt.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Import(ControllerTestConfig.class)
class CommentControllerTest {

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

    @MockitoBean CommentService commentService;
    @MockitoBean JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("POST /api/comments - JWT 인증 + 댓글 생성 200")
    @WithMockUser
    void create_success() throws Exception {
        given(commentService.create(eq(1L), eq("댓글 내용")))
                .willReturn(new CommentResponseDto(1L, "댓글 내용"));

        mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "postId", 1,
                                "content", "댓글 내용"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("댓글 내용"))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("POST /api/comments - Validation 실패 400")
    @WithMockUser
    void create_validationFail() throws Exception {
        mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", ""   // postId @NotNull 누락, content @NotBlank 위반
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/comments - JWT 없음 401")
    void create_unauthorized() throws Exception {
        mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "postId", 1,
                                "content", "댓글"
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/comments/post/{postId} - 댓글 목록 200")
    @WithMockUser
    void list_success() throws Exception {
        given(commentService.findByPost(1L)).willReturn(List.of(
                new CommentResponseDto(1L, "댓글1"),
                new CommentResponseDto(2L, "댓글2")
        ));

        mockMvc.perform(get("/api/comments/post/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("댓글1"));
    }

    @Test
    @DisplayName("DELETE /api/comments/{id} - 댓글 삭제 200")
    @WithMockUser
    void delete_success() throws Exception {
        mockMvc.perform(delete("/api/comments/1"))
                .andExpect(status().isOk());
    }
}
