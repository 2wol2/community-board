# JPA N+1 해결기를 쓰면서 공부한 개념 정리

> 블로그 글을 쓰기 전에 내가 직접 이해한 내용을 정리한 글입니다.

---

## 1. JPA와 ORM이란?

### ORM (Object-Relational Mapping)
자바 객체와 DB 테이블을 자동으로 연결해주는 기술이다.

원래는 DB에서 데이터를 꺼내려면 직접 SQL을 써야 했다.

```sql
SELECT * FROM posts WHERE id = 1;
```

ORM을 쓰면 SQL 없이 자바 코드로 같은 작업을 할 수 있다.

```java
postRepository.findById(1L);
```

### JPA (Java Persistence API)
자바에서 ORM을 사용하기 위한 **표준 인터페이스**다. JPA 자체는 규칙(인터페이스)이고, 실제로 동작하는 구현체는 **Hibernate**다.

Spring Boot에서 `spring-data-jpa` 의존성을 추가하면 Hibernate가 자동으로 설정된다.

---

## 2. 연관관계 매핑

### @ManyToOne
"여러 개(Many)가 하나(One)에 속한다"는 관계를 표현한다.

예를 들어 게시글(Post)은 작성자(User) 한 명에 속하므로:

```java
@Entity
public class Post {
    @ManyToOne
    @JoinColumn(name = "userId")  // DB에서 외래키 컬럼명
    private User user;
}
```

이렇게 하면 JPA가 자동으로 `posts.userId = users.id`로 조인해준다.

### @OneToMany
반대 방향이다. User 입장에서는 게시글이 여러 개다.

```java
@Entity
public class User {
    @OneToMany(mappedBy = "user")  // Post의 user 필드를 참조
    private List<Post> posts;
}
```

---

## 3. Fetch 전략 (핵심 개념)

연관된 엔티티를 **언제** 가져올지 결정하는 전략이다.

### EAGER (즉시 로딩)
연관 엔티티를 **즉시** 함께 가져온다.

```java
@ManyToOne(fetch = FetchType.EAGER)  // 기본값
private User user;
```

Post를 조회하는 순간 User도 바로 조회한다. 편하지만 필요 없을 때도 가져오는 문제가 있다.

### LAZY (지연 로딩)
연관 엔티티를 **실제로 접근할 때** 가져온다.

```java
@ManyToOne(fetch = FetchType.LAZY)
private User user;
```

Post를 조회해도 User는 바로 가져오지 않는다. `post.getUser()`를 호출하는 순간 그때 DB에서 가져온다.

### 왜 LAZY가 기본적으로 더 좋은가?
목록 조회처럼 User 정보가 필요 없는 경우엔 쿼리를 날릴 필요가 없다. 필요할 때만 가져오는 게 더 효율적이다.

> **정리**  
> `@ManyToOne`의 기본값은 EAGER  
> `@OneToMany`의 기본값은 LAZY  
> 실무에서는 모두 LAZY로 바꾸는 게 일반적이다

---

## 4. N+1 문제

### 무엇인가?
1번의 쿼리를 날렸는데 추가로 N번의 쿼리가 더 발생하는 문제다.

### 왜 발생하는가?
`@ManyToOne`의 기본값이 EAGER이기 때문이다.

게시글 10개를 조회하면:
1. `SELECT * FROM posts` → 1번
2. 각 게시글의 User를 로딩 → 10번
3. 합계: **11번 (N+1)**

### 실제 콘솔 로그

```
Hibernate: select p from Post p
Hibernate: select u from User u where u.id=?   -- post 1의 user
Hibernate: select u from User u where u.id=?   -- post 2의 user
Hibernate: select u from User u where u.id=?   -- post 3의 user
...
```

게시글이 1000개면 1001번의 쿼리가 실행된다.

---

## 5. N+1 해결 방법들

### 방법 1. LAZY로 변경 (기본)
```java
@ManyToOne(fetch = FetchType.LAZY)
private User user;
```
가장 기본적인 해결책이다. 필요할 때만 쿼리를 날린다.

### 방법 2. JOIN FETCH
LAZY로 바꿔도 연관 엔티티가 필요한 경우엔 여전히 추가 쿼리가 나간다. 이때 JOIN FETCH를 쓰면 한 번에 다 가져올 수 있다.

```java
@Query("SELECT p FROM Post p JOIN FETCH p.comments WHERE p.id = :id")
Optional<Post> findPostWithComments(@Param("id") Long id);
```

게시글과 댓글을 1번의 쿼리로 함께 가져온다.

### 방법 3. ID로 직접 조회
```java
// 불필요한 패턴 (2쿼리)
Post post = postRepository.findById(postId).orElseThrow();
commentRepository.findByPost(post);

// 개선된 패턴 (1쿼리)
commentRepository.findByPostId(postId);
```

Post 엔티티를 먼저 가져올 필요 없이 postId로 바로 조회하면 된다.

### 방법 4. existsBy vs findBy
```java
// findBy: 객체 전체를 가져옴
postLikeRepository.findByUserAndPost(user, post).isPresent();

// existsBy: 존재 여부만 확인 (더 가볍고 의도도 명확)
postLikeRepository.existsByUserAndPost(user, post);
```

중복 체크처럼 "있냐 없냐"만 필요한 경우엔 `existsBy`가 낫다.

---

## 6. Hibernate 1차 캐시

### 무엇인가?
같은 트랜잭션 안에서 한 번 조회한 엔티티를 메모리에 저장해두는 캐시다. 같은 id로 다시 조회하면 DB에 안 가고 캐시에서 바로 반환한다.

### 왜 중요한가?
N+1 테스트할 때 삽질한 이유가 여기 있었다.

게시글 1000개를 `user_id=1` 하나에 몰아넣었더니:
1. 첫 번째 게시글의 User 조회 → DB 쿼리 1번
2. 두 번째~1000번째 게시글의 User → **캐시에서 바로 반환** (쿼리 안 날아감)

그래서 N+1이 재현이 안 됐다. 정확한 테스트를 위해선 게시글마다 다른 user_id를 써야 한다.

### 구조

```
트랜잭션 시작
    ↓
findById(1) 호출 → DB 조회 → 1차 캐시에 저장
    ↓
findById(1) 다시 호출 → 1차 캐시에서 반환 (DB 안 감)
    ↓
트랜잭션 종료 → 1차 캐시 비워짐
```

---

## 7. @ParameterizedTest

### 무엇인가?
여러 케이스를 하나의 테스트 메서드로 반복 실행할 수 있게 해주는 JUnit5 기능이다.

### 왜 쓰는가?
N=10, N=100, N=1000 세 케이스를 각각 테스트 메서드로 만들면 중복 코드가 생긴다. `@ParameterizedTest`를 쓰면 하나의 메서드로 세 케이스를 모두 실행할 수 있다.

### 사용법

```java
@ParameterizedTest(name = "게시글 {0}개 조회")
@ValueSource(ints = {10, 100, 1000})  // 여기 있는 값들이 순서대로 들어감
void 게시글_목록_조회_테스트(int limit) {
    // limit에 10, 100, 1000이 순서대로 들어와서 3번 실행됨
    List<Post> posts = postRepository.findAll(
        PageRequest.of(0, limit)
    ).getContent();

    assertThat(posts).hasSize(limit);
}
```

---

## 8. Spring Data JPA Repository 메서드 규칙

Spring Data JPA는 메서드 이름만으로 쿼리를 자동 생성한다.

| 메서드명 | 생성되는 SQL |
|---------|------------|
| `findByPostId(Long postId)` | `SELECT * FROM comments WHERE post_id = ?` |
| `countByPostId(Long postId)` | `SELECT COUNT(*) FROM post_likes WHERE post_id = ?` |
| `existsByUserAndPost(User user, Post post)` | `SELECT EXISTS(SELECT 1 FROM post_likes WHERE user_id = ? AND post_id = ?)` |
| `findByUserAndPost(User user, Post post)` | `SELECT * FROM post_likes WHERE user_id = ? AND post_id = ?` |

규칙:
- `find` → SELECT (객체 반환)
- `count` → COUNT (숫자 반환)
- `exists` → EXISTS (boolean 반환)
- `By` 뒤에 조건 필드명을 붙임
- `And`로 여러 조건 연결

---

## 9. Pageable (페이징)

한 번에 모든 데이터를 가져오면 DB와 서버 모두 부담이 크다. 페이징은 데이터를 일정 개수씩 나눠서 가져오는 방식이다.

```java
// 0번째 페이지, 10개씩 가져오기
PageRequest.of(0, 10)

// Repository에서 사용
Page<Post> findAll(Pageable pageable);
```

`Page<Post>`에는 데이터뿐 아니라 전체 개수, 전체 페이지 수 등도 포함된다.

---

## 정리

| 개념 | 핵심 한 줄 요약 |
|------|---------------|
| ORM/JPA | SQL 없이 자바 코드로 DB 조작 |
| EAGER | 연관 엔티티를 즉시 가져옴 (기본값) |
| LAZY | 연관 엔티티를 필요할 때 가져옴 (권장) |
| N+1 문제 | 1번 조회에 N번 추가 쿼리 발생 |
| JOIN FETCH | 연관 엔티티를 1번 쿼리로 함께 조회 |
| 1차 캐시 | 같은 트랜잭션 내 동일 엔티티 재조회 시 캐시 반환 |
| @ParameterizedTest | 여러 케이스를 하나의 테스트 메서드로 실행 |
| existsBy | 존재 여부만 확인, findBy보다 가볍고 의도 명확 |

---

## 마치며

이 개념들을 이해하고 나서 N+1 해결기를 다시 읽으면 훨씬 잘 이해된다. 특히 **Fetch 전략**과 **1차 캐시**는 JPA를 쓰면서 반드시 알아야 하는 개념이다.

면접에서도 "JPA N+1 문제가 무엇인가요?"라는 질문이 자주 나오는데, 이 글의 내용을 이해하고 있으면 충분히 답할 수 있다.
