package com.example.community.domain.application;

import com.example.community.domain.post.Post;
import com.example.community.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 스터디 지원 정보
 */
@Entity
@Table(
    name = "applications",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "post_id"})
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(length = 500)
    private String message;  // 지원 메시지 (선택)

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * 지원 수락
     */
    public void accept() {
        this.status = ApplicationStatus.ACCEPTED;
    }

    /**
     * 지원 거절
     */
    public void reject() {
        this.status = ApplicationStatus.REJECTED;
    }

    /**
     * 지원 대기 중인지 확인
     */
    public boolean isPending() {
        return this.status == ApplicationStatus.PENDING;
    }

}
