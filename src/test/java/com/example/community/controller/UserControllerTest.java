package com.example.community.controller;

import com.example.community.domain.user.UserService;
import com.example.community.global.jwt.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static com.example.community.TestFixtures.createUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Import(ControllerTestConfig.class)
class UserControllerTest {

    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean UserService userService;
    @MockitoBean JwtTokenProvider jwtTokenProvider;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("POST /api/users/register - 성공 201")
    void register_success() throws Exception {
        given(userService.register(any(), any(), any()))
                .willReturn(createUser("newuser", "new@test.com"));

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "newuser",
                                "email", "new@test.com",
                                "password", "password123"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("newuser"))
                .andExpect(jsonPath("$.data.email").value("new@test.com"));
    }

    @Test
    @DisplayName("POST /api/users/register - Validation 실패 400")
    void register_validationFail() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "a",       // @Size(min=2) 위반
                                "email", "not-email",  // @Email 위반
                                "password", "short"    // @Size(min=8) 위반
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/users/me - JWT 인증 성공 200")
    @WithMockUser(username = "user1")
    void me_authenticated() throws Exception {
        given(userService.findByUsername("user1"))
                .willReturn(createUser("user1", "user1@test.com"));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("user1"));
    }

    @Test
    @DisplayName("GET /api/users/me - JWT 없음 401")
    void me_unauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
