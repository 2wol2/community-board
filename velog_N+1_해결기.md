# JPA N+1 문제를 직접 발견하고 수치로 증명하기까지

## 들어가며

Spring Boot로 커뮤니티 API 서버를 개발하던 중, 게시글 목록을 조회할 때 쿼리가 비정상적으로 많이 찍히는 걸 발견했다.

처음엔 "동작은 하니까 괜찮겠지"라고 넘어가려 했는데, 데이터가 늘어날수록 분명히 문제가 될 구조라는 걸 알았다. 그래서 원인을 끝까지 파고들었고, 수치로 검증하기까지의 과정을 기록한다.

---

## 문제 발견

`show-sql: true` 설정으로 콘솔을 보던 중 이상한 걸 발견했다.

게시글 10개를 조회했는데 쿼리가 12개나 찍혔다.

```sql
-- 게시글 목록 조회 1번
SELECT * FROM posts

-- 각 게시글의 작성자 조회 10번
SELECT * FROM users WHERE id = 1
SELECT * FROM users WHERE id = 2
SELECT * FROM users WHERE id = 3
...
```

게시글 N개를 조회하면 작성자 로딩을 위해 N번 쿼리가 추가 실행되는 구조였다. 이게 바로 **N+1 문제**다.

---

## 원인 분석

`Post` 엔티티를 보니 이렇게 되어 있었다.

```java
@ManyToOne  // fetch 전략 미지정 = 기본값 EAGER
@JoinColumn(name = "userId")
private User user;
```

`@ManyToOne`의 기본 fetch 전략은 **EAGER**다. EAGER는 연관된 엔티티를 즉시 로딩하기 때문에, 게시글을 조회할 때마다 해당 게시글의 작성자를 개별 쿼리로 가져온다.

게시글이 1000개라면? **1001번의 쿼리**가 실행된다.

---

## 해결 과정

### 수정 1 - 모든 @ManyToOne을 LAZY로 변경

```java
// Post.java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "userId")
private User user;

// Comment.java
@ManyToOne(fetch = FetchType.LAZY)
private Post post;

// PostLike.java
@ManyToOne(fetch = FetchType.LAZY)
private User user;

@ManyToOne(fetch = FetchType.LAZY)
private Post post;
```

LAZY로 바꾸면 연관 엔티티를 실제로 접근할 때만 쿼리가 실행된다. 목록 조회처럼 작성자 정보가 필요 없는 경우엔 추가 쿼리가 아예 안 나간다.

---

### 수정 2 - 불필요한 엔티티 선조회 제거

댓글 목록 조회 코드를 보니 이런 패턴이 있었다.

```java
// 수정 전: post를 먼저 로딩한 뒤 댓글 조회 (2쿼리)
Post post = postRepository.findById(postId).orElseThrow();
commentRepository.findByPost(post);

// 수정 후: postId로 바로 조회 (1쿼리)
commentRepository.findByPostId(postId);
```

Post 엔티티를 먼저 가져온 다음 그걸 파라미터로 넘기는 방식은 불필요한 쿼리를 한 번 더 날린다. postId로 바로 조회하면 된다.

좋아요 수 조회도 동일하게 수정했다.

```java
// 수정 전 (2쿼리)
Post post = postRepository.findById(postId).orElseThrow();
postLikeRepository.countByPost(post);

// 수정 후 (1쿼리)
postLikeRepository.countByPostId(postId);
```

---

### 수정 3 - 중복 좋아요 체크 개선

```java
// 수정 전: Optional 객체 조회
postLikeRepository.findByUserAndPost(user, post)
    .ifPresent(l -> { throw new CustomException(ErrorCode.ALREADY_LIKED); });

// 수정 후: 존재 여부만 확인
if (postLikeRepository.existsByUserAndPost(user, post)) {
    throw new CustomException(ErrorCode.ALREADY_LIKED);
}
```

`findBy`는 객체를 가져오고 `existsBy`는 존재 여부만 확인한다. 중복 체크 목적이라면 `existsBy`가 의도도 명확하고 성능도 좋다.

---

## 수치 검증

"고쳤다"로 끝내지 않고 실제로 얼마나 개선됐는지 측정했다.

`@ParameterizedTest`로 N=10/100/1000 케이스를 자동화 검증했다. (MySQL 환경)

```java
@ParameterizedTest(name = "게시글 {0}개 조회 쿼리 수 검증")
@ValueSource(ints = {10, 100, 1000})
void 게시글_목록_조회_쿼리수_검증(int limit) {
    List<Post> posts = postRepository.findAll(
        PageRequest.of(0, limit)
    ).getContent();

    assertThat(posts).hasSize(limit);
    System.out.printf("N=%d | 소요시간: %dms%n", limit, elapsed);
}
```

**측정 결과**

| 데이터 수 | 수정 전 쿼리 | 수정 후 쿼리 | 수정 전 응답 | 수정 후 응답 |
|----------|------------|------------|------------|------------|
| 10개 | 12개 | 2개 | 86ms | 5ms |
| 100개 | 102개 | 2개 | 75ms | 11ms |
| **1000개** | **1,002개** | **2개** | **353ms** | **23ms** |

수정 전에는 데이터가 늘어날수록 쿼리 수가 선형으로 증가했다. 수정 후에는 데이터 규모와 무관하게 항상 2개로 고정됐다.

---

## 삽질 기록 - Hibernate 1차 캐시 함정

처음 테스트할 때 이상한 현상이 있었다.

분명히 게시글 1000개를 조회했는데 쿼리가 2개밖에 안 찍혔다. N+1이 수정 전 코드에서도 안 일어나는 것처럼 보였다.

원인은 **Hibernate 1차 캐시**였다.

테스트 데이터를 user_id=1 하나에 게시글 1000개를 몰아넣었더니, 첫 번째 user를 조회한 이후 동일한 user_id 요청은 DB를 안 타고 캐시에서 바로 반환했던 것이다.

**해결:** user 1000명을 각각 만들고, 각 user에 post 1개씩 할당했다. 이렇게 하니 N+1이 정확하게 재현됐다.

```sql
-- 1000명의 서로 다른 user 생성
INSERT INTO users (username, email, password)
SELECT CONCAT('user', seq), CONCAT('user', seq, '@test.com'), 'pw'
FROM (...) numbers WHERE seq <= 1000;

-- 각 user에 post 1개씩 할당
INSERT INTO posts (title, content, userId, view_count)
SELECT CONCAT('제목 ', seq), CONCAT('내용 ', seq), seq, 0
FROM (...) numbers WHERE seq <= 1000;
```

이 삽질 덕분에 Hibernate 1차 캐시 동작 방식을 제대로 이해하게 됐다.

---

## 마무리

N+1 문제는 JPA를 쓰면 거의 필연적으로 마주치는 문제다. 중요한 건 단순히 "LAZY로 바꿨습니다"가 아니라, 왜 문제가 생겼는지 이해하고, 실제로 얼마나 개선됐는지 수치로 확인하는 것이다.

특히 Hibernate 1차 캐시 때문에 N+1이 재현이 안 됐던 경험은, 테스트 환경을 얼마나 정확하게 구성하는지가 중요하다는 걸 알게 해줬다.

다음엔 페이지네이션 최적화(오프셋 vs 커서 방식)를 다뤄볼 예정이다.
