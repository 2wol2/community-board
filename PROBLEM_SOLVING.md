# 🔧 문제 해결 과정 (Problem Solving Journey)

> **각 기술적 도전 과제를 "문제 → 원인 → 해결 → 결과" 구조로 정리**

---

## 1. N+1 쿼리 문제 해결

### 📌 문제 (Problem)

**게시글 1,000개를 조회할 때 1,002개의 쿼리가 실행되어 응답이 느림**

```java
// 증상
GET /api/posts 호출 시
- 응답 시간: 353ms
- 실행된 쿼리: 1,002개
- 서버 로그에 동일한 SELECT 쿼리 1,000번 반복
```

### 🔍 원인 (Root Cause)

**연관 엔티티 접근 시 개별 조회로 인한 N+1 쿼리 발생**

```java
@Entity
public class Post {
    @ManyToOne(fetch = FetchType.LAZY)  // LAZY 설정
    private User user;
}

// 실행되는 쿼리
List<Post> posts = postRepository.findAll();  // 1. 게시글 조회 (1개 쿼리)

for (Post post : posts) {
    post.getUser().getUsername();  // 2. 각 게시글마다 작성자 조회 (N개 쿼리)
}

// 결과: 1 + N = 1,002개 쿼리
```

**왜 발생했는가?**
1. 연관 엔티티(User)에 접근하는 시점에 개별 SELECT 쿼리 발생
2. **LAZY든 EAGER든 N+1은 발생 가능** (핵심은 개별 조회 여부)
3. 게시글 목록 조회 시 작성자 정보가 필요했지만, JOIN 없이 각각 따로 조회됨

### ✅ 해결 (Solution)

**JOIN FETCH를 사용하여 연관 엔티티를 한 번에 로딩**

```java
// PostRepository.java
public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("SELECT p FROM Post p JOIN FETCH p.user")
    List<Post> findAllWithUser();
}
```

**해결 과정**
1. Hibernate 로그 활성화로 쿼리 개수 확인
2. Hibernate Statistics로 정확한 측정
   ```java
   Statistics stats = sessionFactory.getStatistics();
   stats.clear();
   postService.findAll();
   System.out.println("Query Count: " + stats.getPrepareStatementCount());
   ```
3. JOIN FETCH 쿼리 작성
4. 테스트로 검증

### 📊 결과 (Result)

| 지표 | Before | After | 개선율 |
|------|--------|-------|--------|
| **쿼리 수** | 1,002개 | 2개 | **99.8% ↓** |
| **응답 시간** | 353ms | 23ms | **93% ↓** |
| **사용자 경험** | 느림 | 빠름 | ✅ |

**실제 실행 쿼리**
```sql
-- Before: 1,002개 쿼리
SELECT * FROM posts;
SELECT * FROM users WHERE id = 1;
SELECT * FROM users WHERE id = 2;
... (1,000번 반복)

-- After: 2개 쿼리
SELECT p.*, u.*
FROM posts p
INNER JOIN users u ON p.user_id = u.id;
```

**측정 방법**
- Hibernate Statistics API 사용
- 로컬 환경에서 1,000개 데이터 생성 후 측정
- 3회 Warm-up 후 5회 측정 평균
- DB 슬로우 쿼리 로그 병행 확인
- 캐시 비활성화 상태에서 측정
- 동일 데이터셋으로 Before/After 비교

**배운 점**
- "느낌"이 아닌 "측정"으로 문제 파악
- FetchType의 트레이드오프 이해 (LAZY vs EAGER vs JOIN FETCH)
- 성능 최적화의 중요성 체득

---

## 2. 동시성 제어 문제

### 📌 문제 (Problem)

**100명이 동시에 같은 게시글에 좋아요를 누르면 중복 레코드가 생성됨**

```java
// 증상
- 한 사용자가 같은 게시글에 좋아요 2개 이상 생성
- 좋아요 수가 실제보다 많이 카운트됨
- 데이터 정합성 문제 발생
```

### 🔍 원인 (Root Cause)

**Race Condition으로 인한 동시성 문제**

```java
// 문제가 있는 코드
public void toggleLike(Long userId, Long postId) {
    Optional<Like> like = likeRepository.findByUserIdAndPostId(userId, postId);

    if (like.isPresent()) {
        likeRepository.delete(like.get());
    } else {
        likeRepository.save(new Like(userId, postId));  // 동시에 여러 번 실행 가능
    }
}
```

**타임라인 분석**
```
Time    Thread 1              Thread 2
----    --------              --------
t1      SELECT (결과: 없음)
t2                            SELECT (결과: 없음)
t3      INSERT (성공)
t4                            INSERT (성공) ← 중복!
```

**왜 발생했는가?**
1. SELECT와 INSERT 사이에 시간차 존재
2. 두 스레드가 동시에 SELECT하면 둘 다 "없음" 확인
3. 두 스레드가 각각 INSERT 실행하여 중복 생성

### ✅ 해결 (Solution)

**검토한 방법들**

**방법 1: 낙관적 락 (@Version)**

```java
@Entity
public class Like {
    @Version
    private Long version;  // JPA 낙관적 락
}

// 이론적 분석 결과
- OptimisticLockException 다수 발생 예상 (충돌률 높음)
- 재시도 로직 구현 복잡
- 사용자 경험 저하 (일부 요청 실패)
→ 채택하지 않음
```

**방법 2: 비관적 락 (@Lock)**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Like> findByUserIdAndPostId(Long userId, Long postId);

// 이론적 분석 결과
- 응답 시간 증가 예상 (락 대기)
- 읽기 작업까지 대기하여 병목 발생
- 데드락 위험 존재
→ 채택하지 않음
```

**방법 3: 분산 락 (Redis)**
- 인프라 복잡도 증가
- 단일 서버 환경에서 과도한 설계
→ 채택하지 않음

**최종 선택: DB 유니크 제약 조건 + @Transactional**

```java
// 1. Entity에 유니크 제약 조건 추가
@Table(uniqueConstraints = {
    @UniqueConstraint(
        name = "uk_user_post",
        columnNames = {"user_id", "post_id"}
    )
})
@Entity
public class Like {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long postId;
}

// 2. Service에 @Transactional 추가
@Transactional
public void toggleLike(Long userId, Long postId) {
    Optional<Like> existingLike = likeRepository
        .findByUserIdAndPostId(userId, postId);

    if (existingLike.isPresent()) {
        likeRepository.delete(existingLike.get());
    } else {
        try {
            likeRepository.save(new Like(userId, postId));
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 인한 중복 시도 무시
            log.warn("Duplicate like attempt: userId={}, postId={}", userId, postId);
        }
    }
}
```

**왜 이 방법을 선택했는가?**
1. **단순성**: 애플리케이션 로직이 간단함
2. **성능**: DB 레벨 제약으로 오버헤드 최소
3. **안정성**: 100% 데이터 정합성 보장
4. **유지보수**: 코드 변경 없이 DB 스키마만 수정

### 📊 결과 (Result)

**JMeter 부하 테스트**
```
설정:
- Thread Group: 100명 동시 사용자
- Ramp-Up Period: 1초
- Loop Count: 1회
- Target: POST /api/posts/1/like
```

| 지표 | Before | After |
|------|--------|-------|
| **중복 레코드** | 78개 | **0개** |
| **성공률** | 22% | **100%** |
| **평균 응답 시간** | 120ms | **45ms** |
| **데이터 정합성** | ❌ | ✅ |

**실제 DB 제약 조건 동작**
```sql
-- 첫 번째 요청: 성공
INSERT INTO post_likes (user_id, post_id) VALUES (1, 123);
-- Query OK, 1 row affected

-- 두 번째 요청 (동시): 제약 조건 위반
INSERT INTO post_likes (user_id, post_id) VALUES (1, 123);
-- ERROR 1062: Duplicate entry '1-123' for key 'uk_user_post'
-- 트랜잭션 자동 롤백
```

**배운 점**
- 동시성 제어 방법의 트레이드오프 (복잡도 vs 성능 vs 안정성)
- "가장 최신 기술"보다 "문제에 맞는 기술" 선택의 중요성
- DB 레벨 제약의 효과적 활용
- 실제 부하 테스트로 검증하는 습관

---

## 3. Refresh Token 시스템 구현

### 📌 문제 (Problem)

**Access Token만 사용하면 보안과 사용자 경험 사이의 트레이드오프 발생**

```
시나리오 1: Access Token 만료 시간 = 1시간
- 장점: 사용자가 자주 로그인 안 해도 됨 (편리함)
- 단점: 토큰 탈취 시 1시간 동안 악용 가능 (보안 취약)

시나리오 2: Access Token 만료 시간 = 5분
- 장점: 토큰 탈취 피해 최소화 (보안 강화)
- 단점: 5분마다 재로그인 필요 (사용자 불편)
```

### 🔍 원인 (Root Cause)

**단일 토큰 방식의 구조적 한계**

```java
// 기존 구조
@PostMapping("/login")
public ApiResponse<String> login(@RequestBody LoginRequest request) {
    String accessToken = authService.login(request);
    return ApiResponse.success(accessToken);  // 단일 토큰만 반환
}

// 만료 시 문제
GET /api/posts
Authorization: Bearer expired_token
→ 401 Unauthorized
→ 사용자 재로그인 필요 (불편함)
```

**왜 문제인가?**
1. 짧은 만료 시간 → 사용자 경험 저하
2. 긴 만료 시간 → 보안 위험 증가
3. 토큰 무효화 불가능 (Stateless JWT 특성)

### ✅ 해결 (Solution)

**Refresh Token + Token Rotation 전략 도입**

**1단계: 이중 토큰 시스템 설계**

```java
// LoginResponseDto.java
@Getter
@AllArgsConstructor
public class LoginResponseDto {
    private String accessToken;   // 15분 만료
    private String refreshToken;  // 7일 만료
}

// AuthService.java
public LoginResponseDto login(String username, String password) {
    // 1. 사용자 검증
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

    if (!passwordEncoder.matches(password, user.getPassword())) {
        throw new CustomException(ErrorCode.INVALID_PASSWORD);
    }

    // 2. 두 가지 토큰 생성
    String accessToken = jwtTokenProvider.createAccessToken(username);   // 15분
    String refreshToken = jwtTokenProvider.createRefreshToken(username); // 7일

    // 3. Refresh Token을 Redis에 저장
    saveRefreshToken(user, refreshToken);

    return new LoginResponseDto(accessToken, refreshToken);
}
```

**2단계: Redis 기반 Refresh Token 저장**

```java
// RefreshToken.java
@Getter
@AllArgsConstructor
@RedisHash("refreshToken")
public class RefreshToken {
    @Id
    private Long userId;  // Redis Key

    private String token;

    @TimeToLive
    private Long expiration;  // TTL (seconds)

    public static RefreshToken of(Long userId, String token, Long expirationMillis) {
        // JWT는 milliseconds, Redis TTL은 seconds
        // 단위 변환 책임을 RefreshToken이 가짐 (SRP 원칙)
        return new RefreshToken(userId, token, expirationMillis / 1000);
    }
}

// RefreshTokenRepository.java
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, Long> {
    // userId로 Refresh Token 조회/저장/삭제
}
```

**3단계: Token Rotation 구현**

```java
// 토큰 갱신 API
@PostMapping("/refresh")
public ApiResponse<LoginResponseDto> refresh(@RequestBody RefreshRequestDto request) {
    LoginResponseDto response = authService.reissueToken(request.getRefreshToken());
    return ApiResponse.success(response);
}

// AuthService.java
public LoginResponseDto reissueToken(String refreshToken) {
    // 1. Refresh Token 검증
    if (!jwtTokenProvider.validateToken(refreshToken)) {
        throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    // 2. username 추출
    String username = jwtTokenProvider.getUsername(refreshToken);
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

    // 3. Redis에 저장된 Refresh Token과 비교
    RefreshToken storedToken = refreshTokenRepository.findById(user.getId())
        .orElseThrow(() -> new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

    if (!storedToken.getToken().equals(refreshToken)) {
        throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    // 4. Token Rotation: 새 Access Token + 새 Refresh Token 모두 발급
    String newAccessToken = jwtTokenProvider.createAccessToken(username);
    String newRefreshToken = jwtTokenProvider.createRefreshToken(username);

    // 5. Redis 업데이트 (기존 토큰 삭제, 새 토큰 저장)
    saveRefreshToken(user, newRefreshToken);

    return new LoginResponseDto(newAccessToken, newRefreshToken);
}
```

**4단계: 로그아웃 시 즉시 무효화**

```java
@PostMapping("/logout")
public ApiResponse<Void> logout(Authentication authentication) {
    String username = authentication.getName();
    authService.logout(username);
    return ApiResponse.success(null);
}

// AuthService.java
public void logout(String username) {
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

    // Redis에서 Refresh Token 삭제 → 즉시 무효화
    refreshTokenRepository.deleteById(user.getId());
}
```

**왜 Redis를 선택했는가?**

| 저장소 | TTL 관리 | 조회 속도 | 즉시 무효화 | 확장성 | 선택 |
|--------|---------|----------|------------|--------|------|
| **MySQL** | 수동 (배치 삭제) | 10-20ms | 불가능 | 제한적 | ❌ |
| **Redis** | 자동 (@TimeToLive) | 0.1ms | 가능 (DELETE) | 높음 | ✅ |

**구체적 이유:**
1. **TTL 자동 관리**: @TimeToLive로 7일 후 자동 삭제 (MySQL은 배치 작업 필요)
2. **빠른 속도**: 메모리 기반 0.1ms vs MySQL 10-20ms (100배 차이)
3. **즉시 무효화**: 로그아웃 시 DELETE 즉시 반영 (MySQL은 쿼리 캐시 무효화 복잡)
4. **확장성**: 분산 환경에서 여러 서버가 같은 Redis 인스턴스 참조 가능

**왜 Token Rotation을 적용했는가?**
1. **재사용 방지**: 기존 Refresh Token은 사용 후 무효화
2. **보안 강화**: 토큰 탈취 시 피해 기간 최소화
3. **이상 감지**: 재사용 시도 감지 가능

### 📊 결과 (Result)

**보안 강화**
| 항목 | Before | After |
|------|--------|-------|
| **Access Token 만료** | 1시간 | **15분** |
| **토큰 탈취 시 피해** | 1시간 동안 악용 가능 | **15분으로 제한** |
| **토큰 무효화** | 불가능 | **즉시 가능** (Redis DELETE) |
| **재사용 방지** | 없음 | **Token Rotation** |

**사용자 경험 개선**
```
Before:
- Access Token 만료 → 재로그인 필요 (불편)

After:
- Access Token 만료 → Refresh Token으로 자동 갱신 (편리)
- 7일 동안 재로그인 불필요
```

**실제 테스트**
```bash
# 1. 로그인
POST /api/auth/login
Response: {
  "accessToken": "eyJhbGc...",  # 15분
  "refreshToken": "eyJhbGc..."  # 7일
}

# 2. Redis 확인
redis-cli> HGETALL refreshToken:1
1) "userId"
2) "1"
3) "token"
4) "eyJhbGc..."
5) "expiration"
6) "604800"

# 3. 15분 후 Access Token 만료
GET /api/posts
Response: 401 Unauthorized

# 4. 토큰 갱신 (자동)
POST /api/auth/refresh
Response: {
  "accessToken": "새토큰...",     # 새 Access Token
  "refreshToken": "새토큰..."     # 새 Refresh Token (Rotation)
}

# 5. 로그아웃
POST /api/auth/logout
redis-cli> HGETALL refreshToken:1
(empty array)  # 즉시 삭제됨
```

**배운 점**
- 보안과 UX의 균형을 맞추는 아키텍처 설계
- Redis의 TTL 기능을 활용한 자동 만료 처리
- Token Rotation으로 보안 강화
- Stateless JWT의 한계와 Stateful 방식의 조합

---

## 4. Redis 캐싱 도입

### 📌 문제 (Problem)

**좋아요 수 조회 시 매번 COUNT 쿼리가 실행되어 DB 부하 증가**

```sql
-- 게시글 상세 조회 시마다 실행
SELECT COUNT(*) FROM post_likes WHERE post_id = 123;
SELECT COUNT(*) FROM post_likes WHERE post_id = 124;
SELECT COUNT(*) FROM post_likes WHERE post_id = 125;
...

-- 인기 게시글의 경우 1초에 수백 번 조회
-- DB Connection Pool 고갈 위험
```

### 🔍 원인 (Root Cause)

**변경 빈도가 낮은 데이터를 매번 DB에서 조회**

```java
// 문제가 있는 코드
public int getLikeCount(Long postId) {
    return likeRepository.countByPostId(postId);  // 매번 DB 쿼리
}

// 호출 빈도
GET /api/posts/123         → likeRepository.countByPostId(123)
GET /api/posts/123/detail  → likeRepository.countByPostId(123)
GET /api/posts (목록)       → 각 게시글마다 COUNT 쿼리
```

**왜 문제인가?**
1. 좋아요 수는 자주 조회되지만 변경은 드뭄
2. COUNT(*) 쿼리는 인덱스가 있어도 비용 발생
3. 동시 요청 시 DB Connection Pool 부족
4. 응답 시간 증가

### ✅ 해결 (Solution)

**Spring Cache + Redis로 캐싱 적용**

**1단계: Redis 설정**

```java
// RedisConfig.java
@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(host, port);
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))  // TTL: 10분
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new GenericJackson2JsonRedisSerializer())
            );

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build();
    }
}
```

**2단계: @Cacheable 적용**

```java
// LikeService.java
@Service
public class LikeService {

    @Cacheable(value = "likeCount", key = "#postId")
    public int getLikeCount(Long postId) {
        return likeRepository.countByPostId(postId);
    }

    @CacheEvict(value = "likeCount", key = "#postId")
    public void toggleLike(Long userId, Long postId) {
        // 좋아요 추가/삭제 시 캐시 무효화
        Optional<Like> like = likeRepository.findByUserIdAndPostId(userId, postId);

        if (like.isPresent()) {
            likeRepository.delete(like.get());
        } else {
            likeRepository.save(new Like(userId, postId));
        }
    }
}
```

**3단계: 캐시 동작 흐름**

**조회 시:**
1. **Cache Hit** → Redis에서 즉시 반환 (0.1ms)
2. **Cache Miss** → DB COUNT 실행 → Redis에 저장 (TTL 10분) → 반환

**쓰기 시:**
- 좋아요 추가/삭제 → **@CacheEvict**로 캐시 무효화
- 다음 조회 시 Cache Miss → 최신 값으로 자동 갱신

**상세 흐름:**
```
1차 조회 (Cache Miss):
  Client → getLikeCount(123)
       ↓
  Redis GET likeCount::123
       ↓ (캐시 없음)
  MySQL COUNT(*) WHERE post_id = 123
       ↓ (결과: 42)
  Redis SET likeCount::123 = 42 (TTL: 10분)
       ↓
  Return 42 (소요: 10ms)

2차 조회 (Cache Hit):
  Client → getLikeCount(123)
       ↓
  Redis GET likeCount::123
       ↓ (캐시 있음: 42)
  Return 42 (소요: 0.1ms)

좋아요 추가 시 (Cache Eviction):
  Client → toggleLike(1, 123)
       ↓
  @CacheEvict → Redis DEL likeCount::123  (캐시 무효화)
       ↓
  MySQL INSERT INTO post_likes ...
       ↓
  다음 조회 시 Cache Miss → 최신 값(43) 자동 캐싱
```

**왜 10분 TTL을 선택했는가?**
1. 실시간성 vs 성능의 균형
2. 10분 안에 좋아요 수가 크게 변할 확률 낮음
3. 너무 긴 TTL → 데이터 정합성 문제
4. 너무 짧은 TTL → 캐시 효과 감소

### 📊 결과 (Result)

**성능 개선**
| 지표 | Before (DB) | After (Redis) | 개선율 |
|------|-------------|---------------|--------|
| **평균 응답 시간** | 10ms | 0.1ms | **99% ↓** |
| **DB 쿼리 수** | 1,000개/분 | 50개/분 | **95% ↓** |
| **DB CPU 사용률** | 60% | 10% | **83% ↓** |
| **캐시 히트율** | - | 95% | - |

**실제 Redis 데이터**
```bash
# 캐시 저장 확인
redis-cli> GET likeCount::123
"42"

redis-cli> TTL likeCount::123
(integer) 587  # 남은 시간 (초)

# 좋아요 추가 후 캐시 삭제 확인
POST /api/posts/123/like

redis-cli> GET likeCount::123
(nil)  # 캐시 삭제됨

# 다음 조회 시 자동으로 새 값 캐싱
GET /api/posts/123/like/count

redis-cli> GET likeCount::123
"43"  # 새 값
```

**부하 테스트 결과**
```
시나리오: 1분 동안 1,000번 조회

Before (DB만):
- DB 쿼리: 1,000개
- 평균 응답: 10ms
- DB Connection 사용: 20개 (동시)

After (Redis 캐싱):
- DB 쿼리: 50개 (Cache Miss)
- Redis 조회: 950개 (Cache Hit)
- 평균 응답: 0.5ms
- DB Connection 사용: 2개 (동시)
```

**배운 점**
- 변경 빈도 낮은 데이터의 캐싱 전략
- TTL 설정의 중요성 (정합성 vs 성능)
- @Cacheable/@CacheEvict 활용법
- Redis의 성능 이점 체감

---

## 5. 단위 변환 책임 분리 (SRP 원칙)

### 📌 문제 (Problem)

**AuthService가 Redis의 내부 구현(초 단위 TTL)을 알아야 함**

```java
// 문제가 있는 코드
@Service
public class AuthService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;  // milliseconds (604800000)

    public void saveRefreshToken(User user, String token) {
        refreshTokenRepository.save(
            RefreshToken.of(
                user.getId(),
                token,
                refreshTokenExpiration / 1000  // ← AuthService가 Redis 단위를 알아야 함
            )
        );
    }
}
```

### 🔍 원인 (Root Cause)

**계층 간 결합도 증가 - 단일 책임 원칙(SRP) 위반**

```
JWT (milliseconds)          Redis (seconds)
      │                          │
      │    AuthService가         │
      │    둘 다 알아야 함        │
      │                          │
      └──────────┬───────────────┘
                 │
         높은 결합도 발생
```

**왜 문제인가?**
1. **계층 간 결합**: AuthService가 Redis TTL이 초 단위라는 것을 알아야 함
2. **변경 영향**: Redis TTL 단위가 바뀌면 AuthService도 수정 필요
3. **책임 불명확**: 단위 변환 책임이 어디에 있는지 불분명
4. **가독성 저하**: `/1000`의 의미를 코드만 보고 파악 어려움

**실제 시나리오**
```java
// 만약 Redis가 milliseconds로 변경된다면?
refreshTokenRepository.save(
    RefreshToken.of(user.getId(), token, refreshTokenExpiration)  // /1000 제거
);

// AuthService의 여러 곳을 수정해야 함 (변경 영향 범위 큼)
```

### ✅ 해결 (Solution)

**RefreshToken 엔티티가 단위 변환 책임을 가지도록 변경**

```java
// RefreshToken.java
@Getter
@AllArgsConstructor
@RedisHash("refreshToken")
public class RefreshToken {

    @Id
    private Long userId;

    private String token;

    @TimeToLive
    private Long expiration;  // Redis TTL (seconds)

    /**
     * Factory Method: JWT 만료 시간(ms)을 Redis TTL(s)로 변환
     *
     * @param userId 사용자 ID
     * @param token Refresh Token
     * @param expirationMillis JWT 만료 시간 (milliseconds)
     * @return RefreshToken 엔티티
     */
    public static RefreshToken of(Long userId, String token, Long expirationMillis) {
        // 단위 변환 책임을 RefreshToken이 가짐
        // JWT의 milliseconds를 Redis TTL의 seconds로 변환
        return new RefreshToken(userId, token, expirationMillis / 1000);
    }
}
```

**개선된 AuthService**

```java
// AuthService.java
@Service
public class AuthService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;  // milliseconds

    private void saveRefreshToken(User user, String token) {
        // AuthService는 JWT의 milliseconds만 알면 됨
        // Redis TTL이 초 단위인지 밀리초 단위인지 몰라도 됨
        refreshTokenRepository.save(
            RefreshToken.of(user.getId(), token, refreshTokenExpiration)
        );
    }
}
```

**책임 분리 다이어그램**

```
Before:
┌──────────────┐
│ AuthService  │
│              │
│ - JWT (ms)   │  ← JWT 단위를 알아야 함
│ - Redis (s)  │  ← Redis 단위도 알아야 함
│ - /1000      │  ← 변환 로직도 가짐
└──────────────┘

After:
┌──────────────┐         ┌──────────────┐
│ AuthService  │         │ RefreshToken │
│              │         │              │
│ - JWT (ms)   │  ──────→│ - JWT (ms)   │
│              │         │ - Redis (s)  │
│              │         │ - /1000      │
└──────────────┘         └──────────────┘
                              ↑
                         단위 변환 책임
```

**왜 이 방법이 좋은가?**

1. **단일 책임 원칙 (SRP)**
   - AuthService: 인증 비즈니스 로직만 담당
   - RefreshToken: Redis 저장 및 단위 변환 담당

2. **변경 영향 최소화**
   ```java
   // Redis TTL이 milliseconds로 변경되어도
   // RefreshToken.of()만 수정하면 됨
   public static RefreshToken of(Long userId, String token, Long expirationMillis) {
       return new RefreshToken(userId, token, expirationMillis);  // /1000 제거
   }
   // AuthService는 변경 불필요!
   ```

3. **가독성 향상**
   ```java
   // Before: 왜 /1000을 하는지 모호함
   RefreshToken.of(userId, token, expiration / 1000)

   // After: 의도가 명확함 (milliseconds를 넘기면 자동 변환)
   RefreshToken.of(userId, token, expiration)
   ```

4. **테스트 용이성**
   ```java
   @Test
   void testRefreshTokenExpiration() {
       // 단위 변환 로직을 독립적으로 테스트 가능
       RefreshToken token = RefreshToken.of(1L, "token", 604800000L);
       assertEquals(604800L, token.getExpiration());  // 초 단위로 변환되었는지 확인
   }
   ```

### 📊 결과 (Result)

**코드 품질 개선**

| 항목 | Before | After |
|------|--------|-------|
| **계층 간 결합도** | 높음 (AuthService가 Redis 알아야 함) | **낮음** (알 필요 없음) |
| **변경 영향 범위** | AuthService 전체 | **RefreshToken만** |
| **단위 변환 위치** | 여러 곳 분산 | **한 곳에 집중** |
| **가독성** | `/1000`의 의미 불명확 | **의도 명확** |

**리팩토링 전후 비교**

```java
// Before: 여러 곳에서 /1000 반복
login() {
    refreshTokenRepository.save(RefreshToken.of(userId, token, expiration / 1000));
}

reissueToken() {
    refreshTokenRepository.save(RefreshToken.of(userId, token, expiration / 1000));
}

// After: 중복 제거 + 책임 명확화
login() {
    saveRefreshToken(user, token);
}

reissueToken() {
    saveRefreshToken(user, token);
}

private void saveRefreshToken(User user, String token) {
    refreshTokenRepository.save(RefreshToken.of(user.getId(), token, refreshTokenExpiration));
}
```

**배운 점**
- SOLID 원칙 중 SRP를 실제 코드에 적용
- 작은 리팩토링도 코드 품질에 큰 영향
- "누가 무엇을 알아야 하는가?"를 명확히 하는 것의 중요성
- Factory Method 패턴의 활용

---

## 6. 중복 코드 제거 (DRY 원칙)

### 📌 문제 (Problem)

**login()과 reissueToken()에서 Refresh Token 저장 로직 중복**

```java
// AuthService.java
public LoginResponseDto login(String username, String password) {
    // ... 사용자 검증 로직

    String accessToken = jwtTokenProvider.createAccessToken(username);
    String refreshToken = jwtTokenProvider.createRefreshToken(username);

    // 중복 코드 1
    refreshTokenRepository.save(
        RefreshToken.of(user.getId(), refreshToken, refreshTokenExpiration)
    );

    return new LoginResponseDto(accessToken, refreshToken);
}

public LoginResponseDto reissueToken(String refreshToken) {
    // ... 토큰 검증 로직

    String newAccessToken = jwtTokenProvider.createAccessToken(username);
    String newRefreshToken = jwtTokenProvider.createRefreshToken(username);

    // 중복 코드 2 (위와 동일)
    refreshTokenRepository.save(
        RefreshToken.of(user.getId(), newRefreshToken, refreshTokenExpiration)
    );

    return new LoginResponseDto(newAccessToken, newRefreshToken);
}
```

### 🔍 원인 (Root Cause)

**동일한 로직이 여러 곳에 분산 - DRY 원칙 위반**

```
login()                    reissueToken()
   │                            │
   ├─ Refresh Token 저장        ├─ Refresh Token 저장
   │  (코드 동일)                │  (코드 동일)
   │                            │
   └─────────┬──────────────────┘
             │
      중복 코드 발생
```

**왜 문제인가?**
1. **유지보수 어려움**: 로직 변경 시 여러 곳 수정 필요
2. **버그 위험**: 한 곳만 수정하고 다른 곳 누락 가능
3. **가독성 저하**: 동일한 코드 반복으로 핵심 로직 파악 어려움

**실제 시나리오**
```java
// RefreshToken 저장 로직 변경 시
// login()과 reissueToken() 둘 다 수정해야 함
// 한 곳만 수정하면 버그 발생!
```

### ✅ 해결 (Solution)

**private 메서드로 추출하여 재사용**

```java
// AuthService.java
public LoginResponseDto login(String username, String password) {
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

    if (!passwordEncoder.matches(password, user.getPassword())) {
        throw new CustomException(ErrorCode.INVALID_PASSWORD);
    }

    String accessToken = jwtTokenProvider.createAccessToken(username);
    String refreshToken = jwtTokenProvider.createRefreshToken(username);

    // 추출된 메서드 사용
    saveRefreshToken(user, refreshToken);

    return new LoginResponseDto(accessToken, refreshToken);
}

public LoginResponseDto reissueToken(String refreshToken) {
    if (!jwtTokenProvider.validateToken(refreshToken)) {
        throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    String username = jwtTokenProvider.getUsername(refreshToken);
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

    RefreshToken storedToken = refreshTokenRepository.findById(user.getId())
        .orElseThrow(() -> new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

    if (!storedToken.getToken().equals(refreshToken)) {
        throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    String newAccessToken = jwtTokenProvider.createAccessToken(username);
    String newRefreshToken = jwtTokenProvider.createRefreshToken(username);

    // 추출된 메서드 사용 (중복 제거)
    saveRefreshToken(user, newRefreshToken);

    return new LoginResponseDto(newAccessToken, newRefreshToken);
}

/**
 * Refresh Token을 Redis에 저장
 * - 기존 토큰이 있으면 덮어씀 (자동 업데이트)
 * - TTL은 refreshTokenExpiration 설정값 사용
 */
private void saveRefreshToken(User user, String refreshToken) {
    refreshTokenRepository.save(
        RefreshToken.of(user.getId(), refreshToken, refreshTokenExpiration)
    );
}
```

**Extract Method 리팩토링 과정**

```
1. 중복 코드 식별
   ┌────────────────────────────────┐
   │ refreshTokenRepository.save(  │
   │   RefreshToken.of(...)         │
   │ );                             │
   └────────────────────────────────┘

2. 메서드 추출
   private void saveRefreshToken(User user, String refreshToken) {
       // 중복된 로직
   }

3. 호출부 교체
   login(): saveRefreshToken(user, refreshToken);
   reissueToken(): saveRefreshToken(user, newRefreshToken);
```

### 📊 결과 (Result)

**코드 품질 지표**

| 항목 | Before | After |
|------|--------|-------|
| **코드 라인 수** | 60줄 | 50줄 (16% 감소) |
| **중복 코드** | 4줄 × 2곳 = 8줄 | **0줄** |
| **수정 포인트** | 2곳 (login, reissue) | **1곳** (saveRefreshToken) |
| **버그 위험** | 높음 (한 곳만 수정 시) | **낮음** |

**유지보수성 개선**

```java
// 예: Refresh Token 저장 시 로그 추가
// Before: 2곳 수정 필요
login() {
    log.info("Saving refresh token");  // 추가
    refreshTokenRepository.save(...);
}
reissueToken() {
    log.info("Saving refresh token");  // 추가 (누락 위험!)
    refreshTokenRepository.save(...);
}

// After: 1곳만 수정
private void saveRefreshToken(User user, String refreshToken) {
    log.info("Saving refresh token for user: {}", user.getId());  // 추가
    refreshTokenRepository.save(
        RefreshToken.of(user.getId(), refreshToken, refreshTokenExpiration)
    );
}
```

**테스트 용이성 향상**

```java
// saveRefreshToken() 메서드를 단위 테스트 가능
@Test
void testSaveRefreshToken() {
    // given
    User user = createUser();
    String token = "test-token";

    // when
    authService.saveRefreshToken(user, token);  // private → package-private for test

    // then
    RefreshToken saved = refreshTokenRepository.findById(user.getId()).get();
    assertEquals(token, saved.getToken());
}
```

**배운 점**
- DRY(Don't Repeat Yourself) 원칙의 실제 적용
- Extract Method 리팩토링 기법
- 중복 제거가 유지보수성에 미치는 영향
- 작은 개선도 누적되면 큰 효과

---

## 6. Redis Sorted Set 랭킹 시스템

### 📌 문제
인기 스터디 Top 10 조회 시 매번 전체 게시글 정렬 (DB ORDER BY)

### 🔍 원인
- 시간 복잡도: O(N log N) - 전체 게시글 수에 비례
- 1,000개 게시글: 761ms, 처리량 131 req/s
- 데이터 증가 시 성능 급격히 저하

### ✅ 해결
Redis Sorted Set으로 실시간 랭킹 유지

```java
// 점수 계산 및 저장 (이벤트 기반 비동기)
@EventListener
@Async
public void handlePostRankingEvent(PostRankingEvent event) {
    double score = (likeCount * 10.0) + viewCount;
    redisTemplate.opsForZSet().add("post:ranking", postId, score);
}

// 조회 (O(log N))
Set<String> topN = redisTemplate.opsForZSet()
    .reverseRange("post:ranking", 0, 9);
```

### 📈 결과
**부하 테스트 (1,000 요청 / 100 동시 접속)**
- 100개: Redis 164ms vs DB 252ms (35% 개선)
- 1,000개: Redis 130ms vs DB 761ms (83% 개선)
- 처리량: Redis 772 req/s vs DB 131 req/s (5.9배)

---

## 📊 종합 성과 요약

| 문제 | 핵심 해결책 | 정량적 성과 |
|------|-----------|-------------|
| **N+1 쿼리** | JOIN FETCH | 쿼리 99.8% 감소, 응답 93% 개선 |
| **랭킹 성능** | Redis Sorted Set | 83% 개선 (1,000개 기준) |
| **동시성 제어** | DB 유니크 제약 | 중복 100% 방지 |
| **토큰 관리** | Refresh Token + Redis | 보안 강화 + UX 개선 |
| **DB 부하** | Redis 캐싱 | DB 쿼리 95% 감소 |
| **코드 품질** | SRP + DRY 원칙 | 결합도 감소, 중복 제거 |

---

## 💡 핵심 학습 포인트

### 1. 측정 기반 최적화
- "개선했다" → "99.8% 개선했다"
- Hibernate Statistics, JMeter로 정확한 측정
- 데이터 기반 의사결정

### 2. 트레이드오프 이해
- 낙관적 락 vs 비관적 락 vs DB 제약
- 보안 vs 사용자 경험
- 복잡도 vs 성능 vs 안정성

### 3. 설계 원칙 적용
- SRP (단일 책임 원칙)
- DRY (중복 제거)
- 계층 간 결합도 최소화

### 4. 실제 검증
- JMeter 부하 테스트
- Redis CLI 확인
- 단위/통합 테스트

---

이 문서는 각 기술적 도전을 "문제 → 원인 → 해결 → 결과" 구조로 명확히 정리하여,
**왜 이 기술을 선택했는지, 어떻게 해결했는지, 어떤 성과를 냈는지**를 증명합니다.
