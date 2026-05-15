package com.example.community;

import com.example.community.domain.comment.Comment;
import com.example.community.domain.post.Post;
import com.example.community.domain.user.User;

public final class TestFixtures {

    private TestFixtures() {}

    public static User createUser() {
        return createUser("user1", "user1@test.com");
    }

    public static User createUser(String username, String email) {
        return User.builder()
                .id(1L)
                .username(username)
                .email(email)
                .password("encoded")
                .build();
    }

    public static Post createPost() {
        return createPost("제목", "내용");
    }

    public static Post createPost(String title, String content) {
        return Post.builder()
                .id(1L)
                .title(title)
                .content(content)
                .user(createUser())
                .viewCount(0L)
                .build();
    }

    public static Comment createComment() {
        return createComment("댓글 내용");
    }

    public static Comment createComment(String content) {
        return Comment.builder()
                .id(1L)
                .content(content)
                .post(createPost())
                .build();
    }
}
