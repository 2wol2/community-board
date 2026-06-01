package com.example.community.domain.user;

import com.example.community.domain.user.dto.LoginResponseDto;
import com.example.community.global.exception.CustomException;
import com.example.community.global.exception.ErrorCode;
import com.example.community.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static com.example.community.TestFixtures.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @InjectMocks AuthService authService;

    @Test
    @DisplayName("로그인 성공 시 Access Token과 Refresh Token 반환")
    void login_success() {
        // given
        ReflectionTestUtils.setField(authService, "refreshTokenExpiration", 604800000L);

        given(userRepository.findByUsername("user1")).willReturn(Optional.of(createUser()));
        given(passwordEncoder.matches("password1", "encoded")).willReturn(true);
        given(jwtTokenProvider.createAccessToken("user1")).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken("user1")).willReturn("refresh-token");

        // when
        LoginResponseDto response = authService.login("user1", "password1");

        // then
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        verify(jwtTokenProvider).createAccessToken("user1");
        verify(jwtTokenProvider).createRefreshToken("user1");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("존재하지 않는 사용자 로그인 시 USER_NOT_FOUND 예외")
    void login_userNotFound() {
        given(userRepository.findByUsername("noone")).willReturn(Optional.empty());

        CustomException ex = catchThrowableOfType(
                () -> authService.login("noone", "password1"),
                CustomException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("비밀번호 불일치 시 INVALID_PASSWORD 예외")
    void login_invalidPassword() {

        given(userRepository.findByUsername("user1"))
                .willReturn(Optional.of(createUser()));

        given(passwordEncoder.matches("wrong", "encoded"))
                .willReturn(false);

        CustomException ex = catchThrowableOfType(
                () -> authService.login("user1", "wrong"),
                CustomException.class
        );

        assertThat(ex.getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PASSWORD);
        verify(jwtTokenProvider, never()).createAccessToken(any());
        verify(jwtTokenProvider, never()).createRefreshToken(any());
    }
}
