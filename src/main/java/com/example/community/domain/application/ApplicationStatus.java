package com.example.community.domain.application;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 지원 상태
 */
@Getter
@RequiredArgsConstructor
public enum ApplicationStatus {
    PENDING("대기 중", "스터디장의 승인을 기다리고 있습니다"),
    ACCEPTED("수락됨", "스터디 참여가 승인되었습니다"),
    REJECTED("거절됨", "스터디 참여가 거절되었습니다");

    private final String displayName;
    private final String description;
}
