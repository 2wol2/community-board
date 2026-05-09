# Community 게시판 API 서버

> Spring Boot 기반 커뮤니티 백엔드 REST API 서버

---

## 프로젝트 개요

| 항목 | 내용 |
|------|------|
| 개발 기간 | 2025.03 ~ |
| 유형 | 개인 프로젝트 |
| 목적 | RESTful API 설계, JPA 최적화, 인증 구현, 컨테이너 배포 경험 |
| 서버 | Oracle Cloud (Docker) |

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 4.0.3 |
| Security | Spring Security, JWT (jjwt 0.12.6) |
| ORM | Spring Data JPA (Hibernate) |
| Database | MySQL 8.0 |
| Build | Gradle |
| Infra | Docker, Docker Compose, Docker Hub |
| API Docs | Springdoc OpenAPI (Swagger UI) |
| 기타 | Lombok, Bean Validation |

---

## 주요 기능

### 회원 인증
- 회원가입 / 로그인
- JWT 토큰 발급 및 검증
- 인증이 필요한 API 보호

### 게시글
- 게시글 작성 / 수정 / 삭제
- 목록 조회 (전체 / 페이징)
- 키워드 검색 (제목, 내용)
- 상세 조회 (조회수 자동 증가)

### 댓글
- 게시글별 댓글 작성 / 삭제 / 목록 조회

### 좋아요
- 게시글 좋아요 등록 (중복 방지)
- 좋아요 수 조회

---

## 아키텍처

### 패키지 구조
```
com.example.community
├── controller/         # HTTP 요청 진입점, 인증 처리
├── domain/
│   ├── user/           # 회원 도메인 (Entity, Repository, Service)
│   ├── post/           # 게시글 도메인
│   ├── comment/        # 댓글 도메인
│   └── like/           # 좋아요 도메인
└── global/
    ├── config/         # Security, Swagger 설정
    ├── exception/      # 전역 예외 처리
    ├── jwt/            # JWT 필터 및 토큰 프로바이더
    └── response/       # 공통 응답 포맷
```

### 도메인 관계
```
User (1) ──< Post (N)
             ├──< Comment (N)
             └──< PostLike (N) >── User
```

---

## 핵심 기술 설명

### 1. JWT 기반 인증

**왜 JWT를 선택했는가**
- 세션 방식은 서버에 상태를 저장하므로 수평 확장 시 세션 공유 문제가 발생
- JWT는 Stateless 방식으로 서버가 상태를 저장하지 않아 확장성이 좋음
- 토큰 자체에 사용자 정보를 담아 DB 조회 없이 인증 가능

**구현 방식**
- `JwtTokenProvider`: 토큰 생성 / 파싱 / 검증
- `JwtAuthenticationFilter`: 매 요청마다 Authorization 헤더에서 토큰 추출 후 SecurityContext에 등록
- `OncePerRequestFilter` 상속으로 요청당 1회만 실행 보장

```
요청 → JwtAuthenticationFilter → 토큰 검증 → SecurityContext 등록 → Controller
```

---

### 2. N+1 문제 해결

**문제 상황**

`@ManyToOne`의 기본 fetch 전략은 EAGER. 게시글 N개 조회 시 각 게시글의 User를 개별 쿼리로 로딩.

```
SELECT * FROM posts          → 1회
SELECT * FROM users WHERE id = ?  → N회 (게시글 수만큼)
= 총 N+1 쿼리
```

**해결 방법**

| 방법 | 적용 위치 | 효과 |
|------|----------|------|
| `FetchType.LAZY` | Post.user, Comment.post, PostLike.user/post | 실제 접근 시에만 로딩 |
| `JOIN FETCH` | PostRepository.findPostWithComments | 게시글+댓글 1쿼리 |
| `findByPostId()` | CommentRepository | post 사전 조회 제거 |
| `countByPostId()` | PostLikeRepository | post 사전 조회 제거 |

**성능 측정 결과 (MySQL, N=1000)**

| 구분 | 수정 전 | 수정 후 | 감소율 |
|------|---------|---------|--------|
| 쿼리 수 | 1,002개 | 2개 | 99.8% |
| 응답 시간 | 353ms | 23ms | 93% |

> 핵심: 수정 후 쿼리 수는 N에 관계없이 항상 2개(SELECT + COUNT)로 고정

---

### 3. 페이징 및 검색

**페이징**
- Spring Data JPA `Pageable` 사용
- `PageRequest.of(page, size)`로 오프셋 기반 페이징
- 응답에 totalElements, totalPages 포함

**키워드 검색**
```java
@Query("SELECT p FROM Post p WHERE p.title LIKE %:keyword% OR p.content LIKE %:keyword%")
Page<Post> search(String keyword, Pageable pageable);
```
- 제목, 내용 동시 검색
- 페이징과 결합하여 대용량 데이터 처리

---

### 4. 전역 예외 처리

`@RestControllerAdvice` + `ErrorCode` enum으로 일관된 에러 응답 포맷 제공

```json
{
  "success": false,
  "data": null,
  "message": "사용자를 찾을 수 없습니다."
}
```

- 비즈니스 예외는 `CustomException(ErrorCode)`로 던지고 핸들러에서 일괄 처리
- HTTP 상태코드와 메시지를 ErrorCode에서 중앙 관리

---

### 5. 공통 응답 포맷

모든 API 응답을 `ApiResponse<T>`로 래핑하여 클라이언트가 일관된 형식으로 처리 가능

```json
{
  "success": true,
  "data": { ... },
  "message": "요청 성공"
}
```

---

### 6. Docker 기반 배포

**멀티스테이지 빌드**
```dockerfile
FROM eclipse-temurin:17-jdk AS builder  # 빌드 스테이지
RUN ./gradlew bootJar -x test

FROM eclipse-temurin:17-jre             # 실행 스테이지 (JRE만 포함, 이미지 경량화)
COPY --from=builder app.jar .
```
- 빌드 도구가 최종 이미지에 포함되지 않아 이미지 크기 감소

**환경별 설정 분리**

| 파일 | 환경 | 주요 설정 |
|------|------|----------|
| `application.yml` | 공통 | ddl-auto, 기본 프로파일(dev) |
| `application-dev.yml` | 로컬 개발 | localhost DB, show-sql: true |
| `application-prod.yml` | 운영 | 환경변수로 DB 주입, 로깅 OFF |

- 로컬 실행 시 자동으로 dev 프로파일 적용
- Docker 실행 시 `SPRING_PROFILES_ACTIVE=prod`으로 prod 프로파일 적용
- DB 접속 정보를 환경변수로 주입하여 소스코드에 민감 정보 미포함

---

## API 명세

| Method | URI | 설명 | 인증 |
|--------|-----|------|------|
| POST | /api/users/register | 회원가입 | ❌ |
| POST | /api/auth/login | 로그인 (JWT 발급) | ❌ |
| GET | /api/posts | 게시글 목록 | ✅ |
| GET | /api/posts/paged | 게시글 페이징 | ✅ |
| GET | /api/posts/search | 키워드 검색 | ✅ |
| GET | /api/posts/{id} | 게시글 상세 | ✅ |
| POST | /api/posts | 게시글 작성 | ✅ |
| PUT | /api/posts/{id} | 게시글 수정 | ✅ |
| DELETE | /api/posts/{id} | 게시글 삭제 | ✅ |
| GET | /api/posts/{id}/likes | 좋아요 수 조회 | ✅ |
| POST | /api/posts/{id}/like | 좋아요 | ✅ |
| GET | /api/comments/{postId} | 댓글 목록 | ✅ |
| POST | /api/comments | 댓글 작성 | ✅ |
| DELETE | /api/comments/{id} | 댓글 삭제 | ✅ |

---

## 트러블슈팅

### N+1 문제
- **원인**: `@ManyToOne` 기본값이 EAGER fetch라 연관 엔티티를 개별 쿼리로 로딩
- **해결**: 전체 `@ManyToOne`을 LAZY로 변경, 필요한 경우 JOIN FETCH 적용
- **성과**: N=1000 기준 쿼리 수 1002개 → 2개, 응답 시간 93% 감소

### Docker 빌드 오류 (UserDetailsServiceAutoConfiguration)
- **원인**: Spring Boot 4.x에서 패키지 경로 변경으로 exclude 방식 컴파일 오류
- **해결**: `UserService`에 `UserDetailsService` 인터페이스 직접 구현, Spring Security가 자동으로 해당 빈 사용

### 동일 user_id 데이터로 N+1 미재현
- **원인**: 테스트 데이터가 모두 같은 user_id를 가져 Hibernate 1차 캐시로 인해 N+1 미발생
- **해결**: 1000명의 서로 다른 user에 각 1개의 post를 할당하여 정확한 측정

---

## 프로젝트를 통해 익힌 역량

- **JPA 최적화**: N+1 문제 원인 분석 및 LAZY 로딩, JOIN FETCH 적용
- **JWT 인증 구현**: Stateless 인증 흐름 설계 및 Spring Security 필터 연동
- **RESTful API 설계**: 도메인 기반 URL 설계, 공통 응답 포맷 적용
- **성능 측정**: Hibernate Statistics를 활용한 쿼리 수 및 응답 시간 정량적 측정
- **컨테이너 배포**: Docker 멀티스테이지 빌드, docker-compose, Docker Hub 이미지 배포
- **운영 환경 분리**: Spring 프로파일 기반 dev/prod 설정 분리, 환경변수 기반 민감 정보 관리
