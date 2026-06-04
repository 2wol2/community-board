package com.example.community.domain.application;

import com.example.community.domain.application.dto.ApplicationRequestDto;
import com.example.community.domain.application.dto.ApplicationResponseDto;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import com.example.community.domain.post.RecruitStatus;
import com.example.community.domain.user.User;
import com.example.community.domain.user.UserRepository;
import com.example.community.global.exception.CustomException;
import com.example.community.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    /**
     * 스터디 지원하기
     */
    @Transactional
    public ApplicationResponseDto apply(Long postId, String username, ApplicationRequestDto requestDto) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        // 모집 중인지 확인
        if (post.getStatus() != RecruitStatus.RECRUITING) {
            throw new CustomException(ErrorCode.RECRUIT_CLOSED);
        }

        // 중복 지원 확인
        if (applicationRepository.existsByUserAndPost(user, post)) {
            throw new CustomException(ErrorCode.ALREADY_APPLIED);
        }

        // 자기 자신의 스터디에는 지원 불가
        if (post.getUser().getId().equals(user.getId())) {
            throw new CustomException(ErrorCode.CANNOT_APPLY_OWN_POST);
        }

        // 지원 생성
        Application application = Application.builder()
                .user(user)
                .post(post)
                .message(requestDto.getMessage())
                .status(ApplicationStatus.PENDING)
                .build();

        Application saved = applicationRepository.save(application);
        log.info("[지원] postId: {}, username: {}", postId, username);

        return ApplicationResponseDto.from(saved);
    }

    /**
     * 특정 스터디의 지원 목록 조회 (스터디장만 가능)
     */
    @Transactional(readOnly = true)
    public List<ApplicationResponseDto> getApplicationsByPost(Long postId, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        // 스터디장 확인
        if (!post.getUser().getUsername().equals(username)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        return applicationRepository.findByPost(post).stream()
                .map(ApplicationResponseDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 내가 지원한 스터디 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ApplicationResponseDto> getMyApplications(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return applicationRepository.findByUser(user).stream()
                .map(ApplicationResponseDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 지원 수락
     */
    @Transactional
    public void acceptApplication(Long applicationId, String username) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));

        // 스터디장 확인
        if (!application.getPost().getUser().getUsername().equals(username)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        // 이미 처리된 지원인지 확인
        if (!application.isPending()) {
            throw new CustomException(ErrorCode.APPLICATION_ALREADY_PROCESSED);
        }

        // 수락
        application.accept();
        log.info("[지원 수락] applicationId: {}, postId: {}", applicationId, application.getPost().getId());
    }

    /**
     * 지원 거절
     */
    @Transactional
    public void rejectApplication(Long applicationId, String username) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));

        // 스터디장 확인
        if (!application.getPost().getUser().getUsername().equals(username)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        // 이미 처리된 지원인지 확인
        if (!application.isPending()) {
            throw new CustomException(ErrorCode.APPLICATION_ALREADY_PROCESSED);
        }

        // 거절
        application.reject();
        log.info("[지원 거절] applicationId: {}, postId: {}", applicationId, application.getPost().getId());
    }

}
