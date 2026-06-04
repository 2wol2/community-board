package com.example.community.controller;

import com.example.community.domain.application.StudyApplicationService;
import com.example.community.domain.application.dto.StudyApplicationRequestDto;
import com.example.community.domain.application.dto.StudyApplicationResponseDto;
import com.example.community.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Application", description = "스터디 지원 API")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StudyApplicationController {

    private final StudyApplicationService applicationService;

    @Operation(summary = "스터디 지원하기")
    @PostMapping("/posts/{postId}/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StudyApplicationResponseDto> apply(
            @PathVariable Long postId,
            @Valid @RequestBody StudyApplicationRequestDto requestDto,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        StudyApplicationResponseDto response = applicationService.apply(postId, userDetails.getUsername(), requestDto);
        return ApiResponse.success(response);
    }

    @Operation(summary = "스터디 지원 목록 조회 (스터디장 전용)")
    @GetMapping("/posts/{postId}/applications")
    public ApiResponse<List<StudyApplicationResponseDto>> getApplicationsByPost(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        List<StudyApplicationResponseDto> applications = applicationService.getApplicationsByPost(postId, userDetails.getUsername());
        return ApiResponse.success(applications);
    }

    @Operation(summary = "내가 지원한 스터디 목록")
    @GetMapping("/applications/my")
    public ApiResponse<List<StudyApplicationResponseDto>> getMyApplications(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        List<StudyApplicationResponseDto> applications = applicationService.getMyApplications(userDetails.getUsername());
        return ApiResponse.success(applications);
    }

    @Operation(summary = "지원 수락")
    @PostMapping("/applications/{applicationId}/accept")
    public ApiResponse<Void> acceptApplication(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        applicationService.acceptApplication(applicationId, userDetails.getUsername());
        return new ApiResponse<>(true, null, "지원 수락 완료");
    }

    @Operation(summary = "지원 거절")
    @PostMapping("/applications/{applicationId}/reject")
    public ApiResponse<Void> rejectApplication(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        applicationService.rejectApplication(applicationId, userDetails.getUsername());
        return new ApiResponse<>(true, null, "지원 거절 완료");
    }

}
