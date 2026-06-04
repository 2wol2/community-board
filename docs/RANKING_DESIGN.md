# Redis Sorted Set 기반 인기 게시글 랭킹 시스템

## 설계 의도

게시판에서 "인기 게시글 Top 10"을 제공하기 위해 설계했습니다.

**문제 정의**
- 좋아요 수와 조회수 기반으로 게시글을 정렬
- 매 요청마다 전체 게시글을 정렬하면 성능 저하
- 좋아요/조회수가 변경될 때마다 실시간 반영 필요

**목표**
- 대규모 트래픽 환경을 가정한 확장 가능한 설계
- 메인 비즈니스 로직(좋아요, 조회)에 영향 없이 랭킹 업데이트
- Redis 자료구조 활용 경험

---

## 기술 선택 이유

### 1. 왜 Redis Sorted Set인가?

**DB 방식 (ORDER BY)**
```sql
SELECT p.*, COUNT(pl.id) as like_count
FROM posts p
LEFT JOIN post_like pl ON p.id = pl.post_id
GROUP BY p.id
ORDER BY (like_count * 10 + p.view_count) DESC
LIMIT 10;
```
- 시간 복잡도: **O(N log N)** (N = 전체 게시글 수)
- 게시글 10만 개: 약 1,660,000번 비교 연산
- 매 요청마다 전체 정렬 필요

**Redis Sorted Set 방식**
```java
redisTemplate.opsForZSet().reverseRange("post:ranking", 0, 9);
```
- 시간 복잡도: **O(log N + M)** (M = 조회 개수)
- 게시글 10만 개에서 Top 10 조회: 약 17번 연산
- 이미 정렬된 상태에서 범위 조회만

**선택 근거**
- 현재 규모(게시글 100개)에서는 차이 미미
- 게시글 10만 개 이상 가정 시 Redis가 유리
- **학습 목적: Redis 자료구조 활용 경험**

---

### 2. 왜 이벤트 기반인가?

**동기 방식 (직접 호출)**
```java
@Transactional
public void likePost(Long postId, String username) {
    // 좋아요 등록
    postLikeRepository.save(like);

    // 랭킹 업데이트 (동기)
    rankingService.updateScore(postId, ...);  // ← 실패 시 롤백
}
```
❌ 문제:
- Redis 장애 시 좋아요 기능도 중단
- 메인 트랜잭션 응답 시간 증가
- 비즈니스 로직 강결합

**이벤트 기반 (ApplicationEventPublisher)**
```java
@Transactional
public void likePost(Long postId, String username) {
    // 좋아요 등록
    postLikeRepository.save(like);

    // 이벤트 발행 (비동기)
    eventPublisher.publishEvent(new PostRankingEvent(...));
}

@Async
@EventListener
public void handleRankingEvent(PostRankingEvent event) {
    try {
        rankingService.updateScore(...);
    } catch (Exception e) {
        log.error("랭킹 업데이트 실패", e);  // 로그만 남김
    }
}
```
✅ 장점:
- Redis 장애 시에도 좋아요 기능 정상 동작
- 메인 트랜잭션과 분리 (@Async)
- 장애 격리 (try-catch)

---

### 3. 왜 @Async 비동기 처리인가?

**동기 이벤트 리스너**
```
좋아요 요청 → DB 저장 → 이벤트 발행 → Redis 업데이트 → 응답
총 응답 시간 = DB 시간 + Redis 시간
```

**비동기 이벤트 리스너 (@Async)**
```
좋아요 요청 → DB 저장 → 이벤트 발행 → 응답
                            ↓
                    별도 스레드에서 Redis 업데이트
총 응답 시간 = DB 시간만
```

**ThreadPoolTaskExecutor 설정**
```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);       // 기본 스레드 2개
        executor.setMaxPoolSize(10);       // 최대 스레드 10개
        executor.setQueueCapacity(100);    // 대기 큐 100개
        executor.setThreadNamePrefix("async-ranking-");
        return executor;
    }
}
```

---

## 트레이드오프 분석

### 장점

**1. 확장성**
- 게시글 증가 시에도 조회 성능 일정 (O(log N))
- DB 부하 감소

**2. 장애 격리**
- Redis 장애 시에도 좋아요/조회 기능 정상 동작
- 랭킹 조회만 빈 결과 반환

**3. 응답 속도**
- 비동기 처리로 메인 응답 시간 영향 없음
- 좋아요 API: DB 저장만 기다림

### 단점 (트레이드오프)

**1. 복잡도 증가**
- 코드 라인 수 증가: 약 500줄
- 이벤트, 리스너, Service, Controller 분리
- 유지보수 포인트 증가

**2. 데이터 정합성**
- 이벤트 처리 전까지 랭킹이 약간 뒤처질 수 있음
- 비동기이므로 즉시 반영 보장 안 됨
- **허용 가능한 이유**: 랭킹은 "대략적 인기도"만 필요

**3. 디버깅 어려움**
- 비동기 스레드에서 발생한 예외는 로그로만 확인
- 이벤트 발행 → 처리 흐름 추적 어려움

**4. 인프라 의존성**
- Redis 추가 필요 (메모리, 비용)
- 단일 장애점(SPOF) 추가

**5. 오버엔지니어링 가능성**
- 현재 규모(게시글 100개)에서는 MySQL로 충분
- 실제 트래픽 측정 없이 도입하면 과잉 설계

---

## 장애 대응 전략

### 1. Redis 장애 시나리오

**장애 상황**
```
Redis 서버 다운 → 랭킹 업데이트 실패
```

**대응 방법**

**1단계: 예외 무시 (서비스 유지)**
```java
@Async
@EventListener
public void handleRankingEvent(PostRankingEvent event) {
    try {
        rankingService.updateScore(...);
    } catch (Exception e) {
        // Redis 장애 시 로그만 남기고 예외 전파 안 함
        log.error("[랭킹 이벤트] 처리 실패", e);
    }
}
```

**2단계: 랭킹 조회 시 빈 결과 반환**
```java
public Set<Long> getTopRanking(int topN) {
    try {
        return redisTemplate.opsForZSet().reverseRange(...);
    } catch (Exception e) {
        log.error("[랭킹] 조회 실패", e);
        return Set.of();  // 빈 결과 반환
    }
}
```

**결과**
- 좋아요, 조회수 기능: 정상 동작 ✅
- 랭킹 조회: 빈 결과 반환 (UX 저하지만 서비스 중단은 아님)

---

### 2. 이벤트 처리 실패 시나리오

**장애 상황**
```
이벤트 발행 → 비동기 처리 중 예외 → Redis 업데이트 실패
```

**대응 방법**

**1단계: AsyncUncaughtExceptionHandler**
```java
@Override
public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return (ex, method, params) -> {
        log.error("[비동기 예외] 메서드: {}, 파라미터: {}",
                  method.getName(), params, ex);
    };
}
```

**2단계: Dead Letter Queue (향후 개선)**
- 실패한 이벤트를 별도 큐에 저장
- 재시도 로직 추가
- **현재는 미구현** (복잡도 vs 실익)

---

### 3. 모니터링 전략

**필요한 메트릭**
1. 랭킹 업데이트 실패율
2. Redis 응답 시간
3. 이벤트 처리 큐 크기

**로그 수준**
```java
log.debug("[이벤트 발행] postId: {}, score: {}", ...);  // 평소
log.warn("[랭킹] 업데이트 실패 - postId: {}", ...);     // 경고
log.error("[비동기 예외] ...", ex);                      // 에러
```

---

## 성능 최적화

### 1. 조회수 이벤트 최적화

**문제**
```java
// 최초 구현: 조회할 때마다 이벤트 발행
public PostResponseDto findDetail(Long id) {
    post.increaseView();
    publishRankingEvent(id);  // ← 조회 1000번 = 이벤트 1000개
}
```

**개선**
```java
// 최적화: 10번마다 1번만 발행
public boolean increaseView() {
    this.viewCount++;
    return this.viewCount % 10 == 0;  // 10, 20, 30, ... 일 때만 true
}

public PostResponseDto findDetail(Long id) {
    boolean shouldUpdate = post.increaseView();
    if (shouldUpdate) {
        publishRankingEvent(id);  // 조회 1000번 = 이벤트 100개
    }
}
```

**효과**
- 이벤트 발행 90% 감소
- Redis 부하 90% 감소
- 조회수는 좋아요보다 덜 중요하므로 허용

---

### 2. N+1 쿼리 제거

**문제**
```java
// 최초 구현: Top 10 조회 시 20개 쿼리
for (Long postId : topPostIds) {
    Post post = postRepository.findById(postId);           // 쿼리 10번
    long likeCount = postLikeRepository.countByPostId(postId);  // 쿼리 10번
}
```

**개선**
```java
// IN 쿼리로 일괄 조회
List<Post> posts = postRepository.findAllById(postIds);  // 쿼리 1번
Map<Long, Long> likeCountMap =
    postLikeRepository.countLikesByPostIdsAsMap(postIds);  // 쿼리 1번

// @Query
@Query("SELECT pl.post.id AS postId, COUNT(pl) AS likeCount " +
       "FROM PostLike pl WHERE pl.post.id IN :postIds GROUP BY pl.post.id")
List<LikeCountProjection> countLikesByPostIds(@Param("postIds") List<Long> postIds);
```

**효과**
- 쿼리 개수: 20개 → 2개 (90% 감소)
- N+1 문제 해결

---

### 3. 중복 조회 제거

**문제**
```java
// PostLikeService.likePost()
Post post = postRepository.findById(postId).orElseThrow(...);  // 조회 1
// ...
publishRankingEvent(postId);
    // publishRankingEvent 내부
    Post post = postRepository.findById(postId).orElseThrow(...);  // 조회 2
```

**개선**
```java
// Post 객체를 파라미터로 전달
private void publishRankingEvent(Post post) {  // 이미 조회된 엔티티
    long viewCount = post.getViewCount();      // 영속성 컨텍스트에서 조회
    // ...
}
```

**효과**
- @Transactional 내에서 중복 조회 제거
- 영속성 컨텍스트 활용

---

## 구현 상세

### 1. 점수 계산 로직

```java
public double calculateScore() {
    return likeCount * 10.0 + viewCount * 1.0;
}
```

**가중치 설정 근거**
- 좋아요: 10배 (적극적 참여)
- 조회수: 1배 (수동적 참여)
- 좋아요가 더 중요한 인기도 지표

**예시**
```
게시글 A: 좋아요 10개, 조회수 500 → 점수 600
게시글 B: 좋아요 5개, 조회수 800 → 점수 850
→ B가 더 높은 순위 (조회수가 압도적)
```

---

### 2. API 엔드포인트

**Redis 방식**
```
GET /api/posts/ranking?topN=10
→ Redis Sorted Set에서 조회
→ 쿼리 2개 (Post IN, Like COUNT IN)
```

**DB 방식 (비교용)**
```
GET /api/posts/ranking/db?topN=10
→ 전체 게시글 조회 + 정렬
→ 쿼리 2개 (findAll, countLikesByPostIds)
```

---

## 한계 및 개선 방향

### 현재 한계

**1. 규모**
- 현재 게시글 수: 소규모 (테스트 데이터)
- Redis 효과 체감 불가 (MySQL도 충분히 빠름)

**2. 검증 부족**
- 실제 트래픽 없음
- 성능 개선 수치 없음
- "대규모 가정" 설계

**3. 기능 부족**
- 시간 가중치 없음 (오래된 인기글이 계속 상위)
- 카테고리별 랭킹 없음
- 랭킹 변동 추적 없음

---

### 실무에서 도입한다면

**1단계: 측정**
```
- 현재 게시글 수는?
- 일일 조회 수는?
- DB ORDER BY 평균 응답 시간은?
```

**2단계: 임계값 판단**
```
게시글 1만 개 미만 → MySQL로 충분
게시글 10만 개 이상 + 조회 1만 req/day → Redis 고려
```

**3단계: A/B 테스트**
```
트래픽 10% → Redis 방식
트래픽 90% → DB 방식
→ 응답 시간, 에러율 비교
```

**4단계: 점진적 전환**
```
Redis 안정성 확인 후 100% 전환
DB 방식은 Fallback으로 유지
```

---

## 학습 성과

**1. Redis 자료구조**
- Sorted Set (ZADD, ZREVRANGE, ZSCORE, ZRANK)
- StringRedisTemplate 활용
- TTL 없는 영구 데이터

**2. 이벤트 기반 아키텍처**
- ApplicationEventPublisher
- @EventListener + @Async
- 비즈니스 로직 분리

**3. 장애 대응**
- try-catch 예외 격리
- AsyncUncaughtExceptionHandler
- 빈 결과 반환 (Graceful Degradation)

**4. 트레이드오프 분석**
- 복잡도 vs 성능
- 정합성 vs 응답속도
- 인프라 비용 vs 확장성

---

## 결론

**이 프로젝트에서 Redis 랭킹은:**
- ✅ Redis 자료구조 학습
- ✅ 이벤트 기반 설계 경험
- ✅ 비동기 처리 및 장애 대응 이해
- ❌ 실제 성능 개선 검증은 불가 (규모 부족)

**실무 적용 시:**
- 트래픽 측정 필수
- 임계값 기준으로 도입 여부 결정
- A/B 테스트로 검증
- 모니터링 및 알람 구축

**면접 대응:**
```
Q: "왜 Redis를 썼나요?"
A: "대규모 환경을 가정하여 O(log N) 조회가 가능한 Sorted Set을 사용했습니다.
   현재 규모에서는 효과가 미미하지만, 확장성을 고려한 설계 경험을 쌓고자 했습니다."

Q: "성능이 얼마나 좋아졌나요?"
A: "실제 대규모 트래픽이 없어 검증하지 못했습니다.
   실무라면 트래픽 측정 후 A/B 테스트로 도입 여부를 결정했을 것입니다."

Q: "오버엔지니어링 아닌가요?"
A: "네, 현재 규모에서는 오버엔지니어링입니다.
   하지만 이벤트 기반 아키텍처와 Redis 자료구조를 학습하는 것이 목표였습니다."
```
