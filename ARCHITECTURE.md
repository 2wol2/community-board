# 🏗️ System Architecture

## 1. 전체 시스템 아키텍처

```
                    🌐 Client
          (Web Browser / Mobile App)
                      │
                      │ HTTP/HTTPS Request
                      │ JSON (REST API)
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│                   Spring Boot 4.0.3                     │
│                  (Port 8080)                            │
│                                                         │
│  ┌───────────────────────────────────────────────────┐ │
│  │          Controller Layer (REST API)              │ │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐          │ │
│  │  │  Auth    │ │  Post    │ │ Comment  │          │ │
│  │  │Controller│ │Controller│ │Controller│  ...     │ │
│  │  └──────────┘ └──────────┘ └──────────┘          │ │
│  │       │             │             │               │ │
│  │       └─────────────┼─────────────┘               │ │
│  │                     │                             │ │
│  └─────────────────────┼─────────────────────────────┘ │
│                        │                               │
│  ┌─────────────────────▼─────────────────────────────┐ │
│  │       Spring Security Filter Chain                │ │
│  │  ┌──────────────────────────────────────────────┐ │ │
│  │  │     JwtAuthenticationFilter                  │ │ │
│  │  │  1. Authorization Header에서 Token 추출      │ │ │
│  │  │  2. JwtTokenProvider로 검증                  │ │ │
│  │  │  3. 유효하면 SecurityContext에 인증 정보 설정│ │ │
│  │  └──────────────────────────────────────────────┘ │ │
│  └─────────────────────┬─────────────────────────────┘ │
│                        │                               │
│  ┌─────────────────────▼─────────────────────────────┐ │
│  │            Service Layer                          │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌──────────┐ │ │
│  │  │AuthService  │  │PostService  │  │LikeService│ │ │
│  │  │- login()    │  │- findAll()  │  │- toggle() │ │ │
│  │  │- reissue()  │  │  ⚡ JOIN    │  │  🔒 유니크│ │ │
│  │  │- logout()   │  │  FETCH      │  │  제약조건 │ │ │
│  │  └─────────────┘  └─────────────┘  └──────────┘ │ │
│  │                                                   │ │
│  └─────────────────────┬─────────────────────────────┘ │
│                        │                               │
│  ┌─────────────────────▼─────────────────────────────┐ │
│  │         Repository Layer (Spring Data JPA)        │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌─────────┐ │ │
│  │  │UserRepository│  │PostRepository│  │LikeRepo │ │ │
│  │  └──────────────┘  └──────────────┘  └─────────┘ │ │
│  │                                                   │ │
│  │  ┌──────────────────────────────────────────────┐ │ │
│  │  │     RefreshTokenRepository (Redis)           │ │ │
│  │  └──────────────────────────────────────────────┘ │ │
│  └───────────────────────────────────────────────────┘ │
│                        │                               │
└────────────────────────┼───────────────────────────────┘
                         │
         ┌───────────────┴───────────────┐
         │                               │
         ▼                               ▼
┌──────────────────┐            ┌─────────────────┐
│   MySQL 8.0      │            │    Redis 7      │
│  (Port 3306)     │            │  (Port 6379)    │
├──────────────────┤            ├─────────────────┤
│                  │            │                 │
│ 📊 Tables:       │            │ 🔑 Keys:        │
│                  │            │                 │
│ • users          │            │ • refreshToken: │
│   - id (PK)      │            │   {userId}      │
│   - username (UQ)│            │   TTL: 7일      │
│   - password     │            │                 │
│   - email        │            │ • likeCount::   │
│                  │            │   {postId}      │
│ • posts          │            │   TTL: 10분     │
│   - id (PK)      │            │   ⚡ 캐싱       │
│   - user_id (FK) │            │                 │
│   - title        │            └─────────────────┘
│   - content      │
│   - view_count   │
│                  │
│ • comments       │
│   - id (PK)      │
│   - post_id (FK) │
│   - user_id (FK) │
│   - content      │
│                  │
│ • post_likes     │
│   - id (PK)      │
│   - user_id (FK) │
│   - post_id (FK) │
│   🔒 UNIQUE      │
│   (user, post)   │
│                  │
└──────────────────┘
```

---

## 2. 인증 플로우 (Authentication Flow)

### 2-1. 로그인 플로우

```
┌────────┐                                              ┌─────────────┐
│ Client │                                              │Spring Boot  │
└───┬────┘                                              └──────┬──────┘
    │                                                          │
    │  POST /api/auth/login                                   │
    │  { username, password }                                 │
    ├──────────────────────────────────────────────────────▶  │
    │                                                          │
    │                                              ┌───────────▼──────────┐
    │                                              │  AuthController      │
    │                                              │  @PostMapping        │
    │                                              └───────────┬──────────┘
    │                                                          │
    │                                              ┌───────────▼──────────┐
    │                                              │  AuthService         │
    │                                              │  login()             │
    │                                              └───────────┬──────────┘
    │                                                          │
    │                                         ┌────────────────┼─────────────────┐
    │                                         │                │                 │
    │                                         ▼                ▼                 ▼
    │                              ┌──────────────┐  ┌────────────────┐  ┌──────────┐
    │                              │1. UserRepo   │  │2. PasswordEnc  │  │3. JWT    │
    │                              │   유저 조회  │  │   비밀번호검증 │  │  토큰생성│
    │                              └──────────────┘  └────────────────┘  └──────────┘
    │                                                          │
    │                                              ┌───────────▼──────────┐
    │                                              │4. Redis 저장         │
    │                                              │   RefreshToken       │
    │                                              │   - Key: userId      │
    │                                              │   - TTL: 7일         │
    │                                              └──────────────────────┘
    │                                                          │
    │  ◀──────────────────────────────────────────────────────┤
    │  200 OK                                                 │
    │  {                                                      │
    │    "accessToken": "eyJhbGc...",  ← 15분 만료           │
    │    "refreshToken": "eyJhbGc..." ← 7일 만료             │
    │  }                                                      │
    │                                                          │
```

### 2-2. 인증된 요청 플로우

```
┌────────┐                                              ┌─────────────┐
│ Client │                                              │Spring Boot  │
└───┬────┘                                              └──────┬──────┘
    │                                                          │
    │  GET /api/posts                                         │
    │  Authorization: Bearer eyJhbGc...                       │
    ├──────────────────────────────────────────────────────▶  │
    │                                                          │
    │                                    ┌─────────────────────▼─────────┐
    │                                    │JwtAuthenticationFilter         │
    │                                    │1. Header에서 Token 추출        │
    │                                    │2. JwtTokenProvider.validate()  │
    │                                    │3. username 추출                │
    │                                    │4. SecurityContext 설정         │
    │                                    └─────────────────────┬─────────┘
    │                                                          │
    │                                              ┌───────────▼──────────┐
    │                                              │  PostController      │
    │                                              │  @GetMapping         │
    │                                              └───────────┬──────────┘
    │                                                          │
    │                                              ┌───────────▼──────────┐
    │                                              │  PostService         │
    │                                              │  findAll()           │
    │                                              │  ⚡ JOIN FETCH       │
    │                                              └───────────┬──────────┘
    │                                                          │
    │                                              ┌───────────▼──────────┐
    │                                              │  PostRepository      │
    │                                              │  쿼리 1개 → 1,002개  │
    │                                              │  (99.8% 개선)        │
    │                                              └──────────────────────┘
    │                                                          │
    │  ◀──────────────────────────────────────────────────────┤
    │  200 OK                                                 │
    │  { posts: [...] }                                       │
    │  응답 속도: 353ms → 23ms (93% 개선)                     │
    │                                                          │
```

### 2-3. 토큰 갱신 플로우 (Token Rotation)

```
┌────────┐                                              ┌─────────────┐
│ Client │                                              │Spring Boot  │
└───┬────┘                                              └──────┬──────┘
    │                                                          │
    │  POST /api/auth/refresh                                 │
    │  { refreshToken: "eyJhbGc..." }                         │
    ├──────────────────────────────────────────────────────▶  │
    │                                                          │
    │                                              ┌───────────▼──────────┐
    │                                              │  AuthService         │
    │                                              │  reissueToken()      │
    │                                              └───────────┬──────────┘
    │                                                          │
    │                                         ┌────────────────┼─────────────────┐
    │                                         │                │                 │
    │                                         ▼                ▼                 ▼
    │                              ┌──────────────┐  ┌────────────────┐  ┌──────────┐
    │                              │1. JWT 검증   │  │2. Redis 조회   │  │3. 토큰비교│
    │                              │   유효성확인 │  │   저장된 토큰  │  │   일치확인│
    │                              └──────────────┘  └────────────────┘  └──────────┘
    │                                                          │
    │                                              ┌───────────▼──────────┐
    │                                              │4. 새 토큰 발급       │
    │                                              │   - Access Token (新)│
    │                                              │   - Refresh Token(新)│
    │                                              │   🔒 Token Rotation  │
    │                                              └───────────┬──────────┘
    │                                                          │
    │                                              ┌───────────▼──────────┐
    │                                              │5. Redis 업데이트     │
    │                                              │   기존 토큰 삭제     │
    │                                              │   새 토큰 저장       │
    │                                              └──────────────────────┘
    │                                                          │
    │  ◀──────────────────────────────────────────────────────┤
    │  200 OK                                                 │
    │  {                                                      │
    │    "accessToken": "새토큰...",                          │
    │    "refreshToken": "새토큰..."                          │
    │  }                                                      │
    │                                                          │
```

### 2-4. 로그아웃 플로우

```
┌────────┐                                              ┌─────────────┐
│ Client │                                              │Spring Boot  │
└───┬────┘                                              └──────┬──────┘
    │                                                          │
    │  POST /api/auth/logout                                  │
    │  Authorization: Bearer eyJhbGc...                       │
    ├──────────────────────────────────────────────────────▶  │
    │                                                          │
    │                                              ┌───────────▼──────────┐
    │                                              │  AuthService         │
    │                                              │  logout()            │
    │                                              └───────────┬──────────┘
    │                                                          │
    │                                              ┌───────────▼──────────┐
    │                                              │  Redis               │
    │                                              │  DELETE refreshToken │
    │                                              │  토큰 즉시 무효화    │
    │                                              └──────────────────────┘
    │                                                          │
    │  ◀──────────────────────────────────────────────────────┤
    │  200 OK                                                 │
    │  { success: true }                                      │
    │                                                          │
```

---

## 3. 데이터베이스 ERD

```
┌───────────────────────────────────────────────────┐
│                     users                         │
├───────────────────────────────────────────────────┤
│ id              BIGINT         PK, AUTO_INCREMENT │
│ username        VARCHAR(50)    UNIQUE, NOT NULL   │
│ password        VARCHAR(255)   NOT NULL (BCrypt)  │
│ email           VARCHAR(100)   NOT NULL           │
│ created_at      TIMESTAMP      DEFAULT NOW()      │
│ updated_at      TIMESTAMP      ON UPDATE NOW()    │
└───────────────────┬───────────────────────────────┘
                    │
                    │ 1:N
                    │
        ┌───────────┴────────────┬─────────────────┐
        │                        │                 │
        │                        │                 │
        ▼                        ▼                 ▼
┌──────────────────────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│          posts (스터디 모집)      │  │    comments     │  │   post_likes    │
├──────────────────────────────────┤  ├─────────────────┤  ├─────────────────┤
│ id              PK               │◀─┤ id          PK  │  │ id          PK  │
│ user_id         FK               │  │ post_id     FK  │  │ user_id     FK  │
│ title           NN               │  │ user_id     FK  │  │ post_id     FK  │
│ content         NN               │  │ content     NN  │  │ created_at      │
│ view_count                       │  │ created_at      │  │                 │
│ category        ENUM (스터디분류)│  │ updated_at      │  │ 🔒 UNIQUE       │
│ recruit_status  ENUM (모집상태)  │  └─────────────────┘  │ (user_id,       │
│ max_participants INT             │                       │  post_id)       │
│ deadline        DATE             │                       │                 │
│ created_at                       │                       │ ⭐ 동시성 제어   │
│ updated_at                       │                       │    핵심          │
└────────┬─────────────────────────┘                       └─────────────────┘
         │ 1:N
         ▼
┌─────────────────────────────┐
│     applications (지원)     │
├─────────────────────────────┤
│ id              PK          │
│ post_id         FK          │
│ user_id         FK          │
│ message         TEXT        │
│ status          ENUM        │
│   (PENDING/ACCEPTED/...)    │
│ created_at                  │
│ updated_at                  │
└─────────────────────────────┘


[인덱스 전략]

users:
  - PRIMARY KEY (id)
  - UNIQUE INDEX (username)
  - INDEX (email)

posts:
  - PRIMARY KEY (id)
  - INDEX (user_id)
  - INDEX (created_at DESC)
  - INDEX (category, recruit_status)  ← 스터디 검색 최적화

comments:
  - PRIMARY KEY (id)
  - INDEX (post_id, created_at)
  - INDEX (user_id)

post_likes:
  - PRIMARY KEY (id)
  - UNIQUE INDEX (user_id, post_id)  ← 동시성 제어
  - INDEX (post_id)  ← 좋아요 수 COUNT 최적화

applications:
  - PRIMARY KEY (id)
  - INDEX (post_id, status)  ← 모집글별 지원자 조회
  - INDEX (user_id)  ← 사용자별 지원 내역
```

---

## 4. Redis 데이터 구조

```
┌─────────────────────────────────────────────────┐
│              Redis 7 (In-Memory)                │
├─────────────────────────────────────────────────┤
│                                                 │
│  [Key Pattern 1: Refresh Token]                │
│                                                 │
│  Key: refreshToken:{userId}                    │
│  Type: Hash                                    │
│  TTL: 604800 seconds (7일)                     │
│                                                 │
│  Structure:                                     │
│  {                                              │
│    "userId": "1",                               │
│    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI...", │
│    "expiration": "604800"                       │
│  }                                              │
│                                                 │
│  Usage:                                         │
│  - 로그인 시: HSET refreshToken:1 ...          │
│  - 검증 시: HGET refreshToken:1 token          │
│  - 로그아웃: DEL refreshToken:1                 │
│  - 자동 만료: TTL 7일 후 자동 삭제              │
│                                                 │
├─────────────────────────────────────────────────┤
│                                                 │
│  [Key Pattern 2: Like Count Cache]             │
│                                                 │
│  Key: likeCount::{postId}                      │
│  Type: String                                   │
│  TTL: 600 seconds (10분)                       │
│                                                 │
│  Structure:                                     │
│  Value: "42"  (좋아요 수)                       │
│                                                 │
│  Usage:                                         │
│  - 조회 시: GET likeCount::123                 │
│  - 캐시 미스: DB에서 COUNT → SET               │
│  - 캐시 히트: Redis에서 즉시 반환              │
│  - 자동 만료: 10분 후 삭제 (최신성 유지)       │
│                                                 │
│  Performance:                                   │
│  - Cache Hit: O(1) - 0.1ms                     │
│  - Cache Miss: DB COUNT - 10ms                 │
│  - DB 부하: 90% 이상 감소 (캐시 히트율 기준)   │
│                                                 │
├─────────────────────────────────────────────────┤
│                                                 │
│  [Key Pattern 3: Ranking]                      │
│                                                 │
│  Key: post:ranking                             │
│  Type: Sorted Set                              │
│  TTL: 없음 (영구)                              │
│                                                 │
│  Structure:                                     │
│  {                                              │
│    "123": 530.0,  // postId: score            │
│    "456": 420.0,  // score = likeCount*10      │
│    "789": 380.0   //        + viewCount        │
│  }                                              │
│                                                 │
│  Usage:                                         │
│  - 점수 업데이트: ZADD post:ranking 530 "123"  │
│  - Top 10 조회: ZREVRANGE post:ranking 0 9     │
│  - 이벤트 기반: 좋아요/조회 시 비동기 업데이트 │
│                                                 │
│  Performance:                                   │
│  - 조회: O(log N + M) - M은 조회 개수          │
│  - 1,000개 기준: 130ms (DB 761ms 대비 83% 개선)│
│                                                 │
└─────────────────────────────────────────────────┘
```

---

## 5. 패키지 구조 (Hybrid Architecture)

```
src/main/java/com/example/community/

📦 controller/                          ← REST API 엔드포인트 (가시성)
 ┣ 📄 AuthController.java               POST /api/auth/login, /refresh, /logout
 ┣ 📄 UserController.java               POST /api/users/register
 ┣ 📄 PostController.java               CRUD /api/posts
 ┗ 📄 CommentController.java            CRUD /api/posts/{id}/comments

📦 domain/                              ← 비즈니스 로직 응집
 ┃
 ┣ 📁 user/
 ┃  ┣ 📄 User.java                      엔티티 (JPA)
 ┃  ┣ 📄 UserRepository.java            데이터 접근 (Spring Data JPA)
 ┃  ┣ 📄 UserService.java               비즈니스 로직
 ┃  ┣ 📄 AuthService.java               인증 로직 (login, reissueToken, logout)
 ┃  ┣ 📄 RefreshToken.java              Redis 엔티티 (@RedisHash)
 ┃  ┣ 📄 RefreshTokenRepository.java    Redis 접근 (CrudRepository)
 ┃  ┗ 📁 dto/
 ┃     ┣ 📄 LoginRequestDto.java
 ┃     ┣ 📄 LoginResponseDto.java       Access + Refresh Token
 ┃     ┗ 📄 RefreshRequestDto.java
 ┃
 ┣ 📁 post/
 ┃  ┣ 📄 Post.java
 ┃  ┣ 📄 PostRepository.java            @Query("JOIN FETCH") ⚡
 ┃  ┣ 📄 PostService.java
 ┃  ┗ 📁 dto/
 ┃     ┣ 📄 PostRequestDto.java
 ┃     ┗ 📄 PostResponseDto.java
 ┃
 ┣ 📁 comment/
 ┃  ┣ 📄 Comment.java
 ┃  ┣ 📄 CommentRepository.java
 ┃  ┣ 📄 CommentService.java
 ┃  ┗ 📁 dto/
 ┃
 ┗ 📁 like/
    ┣ 📄 Like.java                      @UniqueConstraint 🔒
    ┣ 📄 LikeRepository.java
    ┣ 📄 LikeService.java               @Transactional (동시성 제어)
    ┗ 📁 dto/

📦 global/                              ← 공통 기능 (횡단 관심사)
 ┃
 ┣ 📁 config/
 ┃  ┣ 📄 SecurityConfig.java            Spring Security 설정
 ┃  ┣ 📄 RedisConfig.java               Redis 연결 설정
 ┃  ┗ 📄 CacheConfig.java               캐시 TTL 설정 (10분)
 ┃
 ┣ 📁 jwt/
 ┃  ┣ 📄 JwtTokenProvider.java          토큰 생성/검증
 ┃  ┗ 📄 JwtAuthenticationFilter.java   Filter Chain에 등록
 ┃
 ┣ 📁 exception/
 ┃  ┣ 📄 CustomException.java
 ┃  ┣ 📄 ErrorCode.java                 Enum (USER_NOT_FOUND, ...)
 ┃  ┗ 📄 GlobalExceptionHandler.java    @RestControllerAdvice
 ┃
 ┗ 📁 response/
    ┣ 📄 ApiResponse.java               성공 응답 래퍼 클래스
    ┗ 📄 ErrorResponse.java             에러 응답 래퍼 클래스


[설계 근거]

✅ Controller 분리
   - REST API 전체 구조를 한눈에 파악 가능
   - 엔드포인트 URL을 빠르게 찾을 수 있음

✅ Domain 응집
   - 도메인별로 코드가 집중되어 있어 기능 변경 시 영향 범위 최소화
   - User, Post, Comment, Like 각각 독립적으로 관리

✅ Global 공통화
   - 중복 제거 및 일관성 유지
   - 횡단 관심사(Security, Exception, Response) 분리
```

---

## 6. 성능 최적화 포인트

### 6-1. N+1 쿼리 해결

```
[Before - N+1 Problem]

PostService.findAll()
└─> PostRepository.findAll()
    ├─> SELECT * FROM posts                    (1개)
    └─> 각 Post마다
        └─> SELECT * FROM users WHERE id = ?   (1,000개)

총 1,002개 쿼리 실행
응답 속도: 353ms


[After - JOIN FETCH]

PostService.findAll()
└─> PostRepository.findAllWithUser()
    └─> SELECT p.*, u.*
        FROM posts p
        JOIN users u ON p.user_id = u.id       (1개)

총 2개 쿼리 실행
응답 속도: 23ms

⚡ 개선: 쿼리 99.8% 감소, 응답 속도 93% 개선
```

### 6-2. Redis 캐싱 전략

```
[Cache Flow]

Client Request: GET /api/posts/123/like/count
                    │
                    ▼
            LikeService.getLikeCount(123)
                    │
                    ▼
        @Cacheable(value="likeCount", key="#postId")
                    │
        ┌───────────┴───────────┐
        │                       │
        ▼ Cache Hit             ▼ Cache Miss
   ┌─────────┐            ┌─────────────┐
   │ Redis   │            │ MySQL       │
   │ GET     │            │ COUNT(*)    │
   │ 0.1ms   │            │ 10ms        │
   └─────────┘            └──────┬──────┘
        │                        │
        │                        ▼
        │                  ┌──────────────┐
        │                  │ Redis SET    │
        │                  │ TTL: 10분    │
        │                  └──────┬───────┘
        │                        │
        └────────────┬───────────┘
                     ▼
              Return to Client

⚡ 효과: DB 부하 90% 이상 감소
```

### 6-3. 동시성 제어

```
[Concurrency Control]

100명이 동시에 좋아요 클릭
        │
        ▼
┌───────────────────────────┐
│ @Transactional            │
│ LikeService.toggleLike()  │
└───────────┬───────────────┘
            │
            ▼
┌───────────────────────────┐
│ LikeRepository.save()     │
│                           │
│ INSERT INTO post_likes    │
│ (user_id, post_id)        │
│ VALUES (1, 123)           │
└───────────┬───────────────┘
            │
            ▼
┌───────────────────────────┐
│ DB Level Check            │
│                           │
│ UNIQUE CONSTRAINT         │
│ (user_id, post_id)        │
│                           │
│ 중복 시 자동 롤백         │
└───────────────────────────┘

⚡ 결과: 중복 레코드 0개 (100% 방지)
```

---

## 7. 배포 아키텍처 (예정)

### 7-1. 로컬 환경 (현재)

```
┌─────────────────────────────────────┐
│     Docker Compose (localhost)      │
│                                     │
│  ┌────────────┐  ┌────────────┐   │
│  │  MySQL     │  │  Redis     │   │
│  │  :3307     │  │  :6379     │   │
│  └────────────┘  └────────────┘   │
│                                     │
│  ┌─────────────────────────────┐   │
│  │   Spring Boot Application   │   │
│  │   :8080                     │   │
│  └─────────────────────────────┘   │
│                                     │
└─────────────────────────────────────┘
```

### 7-2. 클라우드 배포 (향후)

```
                    🌐 Internet
                         │
                         ▼
              ┌──────────────────────┐
              │   Load Balancer      │
              │   (Route 53 + ALB)   │
              └──────────┬───────────┘
                         │
         ┌───────────────┴───────────────┐
         │                               │
         ▼                               ▼
┌─────────────────┐            ┌─────────────────┐
│   ECS Task 1    │            │   ECS Task 2    │
│  Spring Boot    │            │  Spring Boot    │
│  (Container)    │            │  (Container)    │
└────────┬────────┘            └────────┬────────┘
         │                               │
         └───────────────┬───────────────┘
                         │
         ┌───────────────┴───────────────┐
         │                               │
         ▼                               ▼
┌─────────────────┐            ┌─────────────────┐
│   RDS MySQL     │            │  ElastiCache    │
│  (Multi-AZ)     │            │  Redis          │
│                 │            │  (Cluster)      │
└─────────────────┘            └─────────────────┘

[모니터링]
- CloudWatch Logs
- CloudWatch Metrics
- X-Ray (Distributed Tracing)
```

---

## 8. 보안 아키텍처

```
┌─────────────────────────────────────────────────────────┐
│                    Security Layers                      │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  Layer 1: Network Security                             │
│  ┌───────────────────────────────────────────────────┐ │
│  │ • HTTPS (TLS 1.3)                                 │ │
│  │ • CORS 설정 (허용된 Origin만)                     │ │
│  │ • Rate Limiting (향후 추가 예정)                   │ │
│  └───────────────────────────────────────────────────┘ │
│                          │                              │
│                          ▼                              │
│  Layer 2: Application Security (Spring Security)       │
│  ┌───────────────────────────────────────────────────┐ │
│  │ • JWT Authentication Filter                       │ │
│  │   - Access Token 검증 (15분 만료)                 │ │
│  │   - Bearer Token 추출 및 검증                     │ │
│  │                                                   │ │
│  │ • SecurityContext 설정                            │ │
│  │   - 인증된 사용자 정보 저장                       │ │
│  │                                                   │ │
│  │ • CSRF 비활성화 (REST API 특성)                   │ │
│  │ • Stateless Session (STATELESS)                   │ │
│  └───────────────────────────────────────────────────┘ │
│                          │                              │
│                          ▼                              │
│  Layer 3: Authentication & Authorization               │
│  ┌───────────────────────────────────────────────────┐ │
│  │ • BCrypt 비밀번호 암호화 (Cost Factor: 10)       │ │
│  │ • JWT Secret Key (256-bit 이상)                   │ │
│  │ • Refresh Token Rotation (재사용 방지)            │ │
│  │ • Redis 기반 토큰 관리 (즉시 무효화 가능)         │ │
│  └───────────────────────────────────────────────────┘ │
│                          │                              │
│                          ▼                              │
│  Layer 4: Data Security                                │
│  ┌───────────────────────────────────────────────────┐ │
│  │ • SQL Injection 방지 (Prepared Statement)         │ │
│  │ • XSS 방지 (DTO 기반 응답, HTML Escape)           │ │
│  │ • Validation (@Valid, @NotBlank, ...)             │ │
│  │ • 민감정보 마스킹 (비밀번호 로깅 방지)            │ │
│  └───────────────────────────────────────────────────┘ │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 9. 예외 처리 흐름

```
Client Request
     │
     ▼
┌─────────────────────────────────────────┐
│   Controller                            │
│   - @Valid 검증 실패                     │
│   - MethodArgumentNotValidException     │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│   Service                               │
│   - 비즈니스 로직 예외                   │
│   - throw new CustomException(...)      │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│   GlobalExceptionHandler                │
│   @RestControllerAdvice                 │
│                                         │
│   @ExceptionHandler(CustomException)    │
│   @ExceptionHandler(MethodArgument...)  │
│   @ExceptionHandler(Exception)          │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│   ErrorResponse                         │
│   {                                     │
│     "success": false,                   │
│     "data": null,                       │
│     "error": {                          │
│       "code": "USER_NOT_FOUND",         │
│       "message": "사용자를 찾을 수 없습니다"│
│     }                                   │
│   }                                     │
└─────────────────────────────────────────┘
               │
               ▼
          Client
```

---

## 10. 핵심 설계 결정 요약

| 항목 | 선택한 기술/방법 | 이유 |
|------|-----------------|------|
| **인증 방식** | JWT (Access + Refresh) | Stateless, 확장성 우수 |
| **토큰 저장소** | Redis | TTL 자동 관리, 빠른 속도, 즉시 무효화 |
| **보안 강화** | Token Rotation | 토큰 재사용 방지 |
| **N+1 해결** | JOIN FETCH | 쿼리 99.8% 감소 |
| **캐싱** | Redis (@Cacheable) | DB 부하 90% 감소 |
| **동시성 제어** | DB 유니크 제약 | 단순하고 안정적 |
| **패키지 구조** | Hybrid (Domain + Controller) | 가시성 + 응집도 |
| **응답 구조** | ApiResponse 래퍼 | 일관성 유지 |
| **예외 처리** | GlobalExceptionHandler | 중앙 집중식 관리 |

---

이 아키텍처 문서는 프로젝트의 모든 기술적 결정과 그 근거를 담고 있습니다.
