package com.example.community.domain.user;

import com.example.community.TestFixtures;
import com.example.community.global.exception.CustomException;
import com.example.community.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static com.example.community.TestFixtures.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks UserService userService;

    @Test
    @DisplayName("회원가입 성공")
    void register_success() {
        given(userRepository.findByUsername("user1")).willReturn(Optional.empty());
        given(passwordEncoder.encode("password1")).willReturn("encoded");
        given(userRepository.save(any())).willReturn(createUser());

        User result = userService.register("user1", "user1@test.com", "password1");

        assertThat(result.getUsername()).isEqualTo("user1");
        verify(passwordEncoder).encode("password1");
        verify(userRepository).save(any());
    }

    @Test
    @DisplayName("중복 사용자명으로 회원가입 시 DUPLICATE_USERNAME 예외")
    void register_duplicateUsername() {
        given(userRepository.findByUsername("user1")).willReturn(Optional.of(createUser()));

        CustomException ex = catchThrowableOfType(
                () -> userService.register("user1", "user1@test.com", "password1"),
                CustomException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_USERNAME);
    }

    @Test
    @DisplayName("사용자명으로 조회 성공")
    void findByUsername_success() {
        given(userRepository.findByUsername("user1")).willReturn(Optional.of(createUser()));

        User result = userService.findByUsername("user1");

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("존재하지 않는 사용자 조회 시 USER_NOT_FOUND 예외")
    void findByUsername_notFound() {
        given(userRepository.findByUsername("noone")).willReturn(Optional.empty());

        CustomException ex = catchThrowableOfType(
                () -> userService.findByUsername("noone"),
                CustomException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
