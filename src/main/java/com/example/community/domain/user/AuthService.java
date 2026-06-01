package com.example.community.domain.user;

import com.example.community.domain.user.dto.LoginResponseDto;
import com.example.community.global.exception.CustomException;
import com.example.community.global.exception.ErrorCode;
import com.example.community.global.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    public LoginResponseDto login(String username, String password) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getUsername());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUsername());

        // Redis에 Refresh Token 저장
        saveRefreshToken(user, refreshToken);

        return new LoginResponseDto(accessToken, refreshToken);
    }

    public LoginResponseDto reissueToken(String refreshToken) {

        // 1. Refresh Token 유효성 검증
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 2. Refresh Token에서 username 추출
        String username = jwtTokenProvider.getUsername(refreshToken);

        // 3. User 조회
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 4. Redis에서 저장된 Refresh Token 조회
        RefreshToken storedToken = refreshTokenRepository.findById(user.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        // 5. 토큰 일치 여부 확인
        if (!storedToken.getToken().equals(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 6. 새로운 Access Token 발급
        String newAccessToken = jwtTokenProvider.createAccessToken(user.getUsername());

        // 7. Refresh Token Rotation - 새로운 Refresh Token 발급
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getUsername());

        // 8. Redis에 새로운 Refresh Token 저장 (기존 것 덮어쓰기)
        saveRefreshToken(user, newRefreshToken);

        return new LoginResponseDto(newAccessToken, newRefreshToken);
    }

    public void logout(String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // Redis에서 Refresh Token 무효화
        refreshTokenRepository.deleteById(user.getId());
    }

    private void saveRefreshToken(User user, String refreshToken) {
        refreshTokenRepository.save(
                RefreshToken.of(
                        user.getId(),
                        refreshToken,
                        refreshTokenExpiration
                )
        );
    }
}