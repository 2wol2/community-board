package com.example.community.domain.post;
import com.example.community.domain.comment.dto.CommentDto;
import com.example.community.domain.post.dto.PostListDto;
import com.example.community.domain.post.dto.PostResponseDto;
import com.example.community.domain.user.User;
import com.example.community.domain.user.UserRepository;
import com.example.community.global.exception.CustomException;
import com.example.community.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public PostListDto create(String username, String title, String content) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Post post = Post.builder()
                .title(title)
                .content(content)
                .user(user)
                .build();

        Post saved = postRepository.save(post);
        return new PostListDto(saved.getId(), saved.getTitle(), saved.getContent());
    }

    public List<PostListDto> findAll(){
        return postRepository.findAll()
                .stream()
                .map(post -> new PostListDto(
                        post.getId(),
                        post.getTitle(),
                        post.getContent()
                ))
                .toList();
    }

    public Post findById(Long id){
        return postRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
    }

    @Transactional
    public PostResponseDto findDetail(Long id){

        Post post = postRepository.findPostWithComments(id);

        post.increaseView();

        List<CommentDto> comments = post.getComments()
                .stream()
                .map(c -> new CommentDto(
                        c.getId(),
                        c.getContent()
                ))
                .toList();

        return new PostResponseDto(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                comments,
                post.getViewCount()
        );
    }
    public Page<PostListDto> findAllPaged(int page, int size){
        Pageable pageable = PageRequest.of(page, size);
        return postRepository.findAll(pageable)
                .map(post -> new PostListDto(
                        post.getId(),
                        post.getTitle(),
                        post.getContent()
                ));
    }

    public Page<PostListDto> search(String keyword, int page, int size){

        Pageable pageable = PageRequest.of(page, size);

        return postRepository.search(keyword, pageable)
                .map(post -> new PostListDto(
                        post.getId(),
                        post.getTitle(),
                        post.getContent()
                ));
    }

    @Transactional
    public PostListDto update(Long postId, String title, String content){
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
        post.setTitle(title);
        post.setContent(content);

        Post saved = postRepository.save(post);
        return new PostListDto(saved.getId(), saved.getTitle(), saved.getContent());
    }

    public void delete(Long postId){
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
        postRepository.delete(post);
    }
}