package com.example.community.domain.post;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 모집 상태
 */
@Getter
@RequiredArgsConstructor
public enum RecruitStatus {
    RECRUITING("모집 중", "스터디원을 모집하고 있습니다"),
    CLOSED("모집 마감", "모집이 마감되었습니다");

    private final String displayName;
    private final String description;
}
