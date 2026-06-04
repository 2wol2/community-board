package com.example.community.domain.post;

import com.example.community.domain.comment.Comment;
import com.example.community.domain.user.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "posts")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="userId")
    private User user;

    @JsonIgnore
    @OneToMany(mappedBy = "post")
    private List<Comment> comments;

    @Builder.Default
    @Column(name = "view_count")
    private long viewCount = 0;

    // 스터디 모집 관련 필드 (nullable - 일반 게시글에는 null)
    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status")
    private RecruitStatus status = RecruitStatus.RECRUITING;

    @Column(name = "max_members")
    private Integer maxMembers;

    @Column(name = "deadline")
    private LocalDateTime deadline;

    // 생성/수정 시간 (Spring Data JPA Auditing)
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 조회수 증가
     *
     * @return 랭킹 업데이트가 필요한지 여부 (10의 배수일 때만 true)
     */
    public boolean increaseView(){
        this.viewCount++;
        // 10번마다 한 번만 랭킹 업데이트 (1, 11, 21, 31, ...)
        // 조회수는 좋아요보다 덜 중요하므로 이벤트 발행 빈도 감소
        return this.viewCount % 10 == 0;
    }

    /**
     * 모집 마감 처리
     */
    public void closeRecruit() {
        this.status = RecruitStatus.CLOSED;
    }

    /**
     * 모집 중인지 확인
     */
    public boolean isRecruiting() {
        return this.status == RecruitStatus.RECRUITING;
    }

}
