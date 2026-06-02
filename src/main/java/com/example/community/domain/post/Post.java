package com.example.community.domain.post;

import com.example.community.domain.comment.Comment;
import com.example.community.domain.user.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "posts")
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

}