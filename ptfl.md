# 이민희 | 백엔드 개발자

> 문제를 발견하면 수치로 증명하기까지 끝내는 백엔드 개발자

📧 이메일: [추가]  
🔗 GitHub: [추가]  
📍 경기도 안양시

---

## 🛠 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 4.0.3 |
| Security | Spring Security, JWT |
| ORM | Spring Data JPA (Hibernate) |
| Database | MySQL 8.0 |
| Build | Gradle |
| Infra | Docker, Docker Compose, Docker Hub |
| API Docs | Springdoc OpenAPI (Swagger UI) |
| Test | JUnit5, @ParameterizedTest |

---

## 📁 프로젝트

### Community API Server
> Spring Boot 기반 커뮤니티 백엔드 REST API 서버

**기간:** 2025.03 ~  
**유형:** 개인 프로젝트  
**GitHub:** [링크 추가]

#### 개요
RESTful API 설계, JPA 최적화, JWT 인증 구현을 목적으로 개발한 커뮤니티 백엔드 서버. 단순 구현에 그치지 않고 성능 문제를 직접 발견하고 수치로 검증하는 데 초점을 맞췄음.

#### 주요 기능
- JWT 기반 회원 인증/인가 (회원가입, 로그인, 토큰 검증)
- 게시글 CRUD, 페이징, 키워드 검색, 조회수 자동 증가
- 댓글 작성/삭제, 좋아요 등록 (중복 방지)
- 글로벌 예외 처리 (ErrorCode + GlobalExceptionHandler)
- Swagger UI API 문서 자동화
- Spring 프로파일로 개발/운영 환경 분리 (dev/prod)

---

#### 🔥 핵심 기술 설명

**1. JWT 기반 인증 설계**

세션 방식은 수평 확장 시 세션 공유 문제가 발생해 JWT Stateless 방식을 선택. 토큰 자체에 사용자 정보를 담아 DB 조회 없이 인증 가능하도록 설계.

```
요청 → JwtAuthenticationFilter → 토큰 검증 → SecurityContext 등록 → Controller
```

- `JwtTokenProvider`: 토큰 생성 / 파싱 / 검증
- `JwtAuthenticationFilter`: `OncePerRequestFilter` 상속으로 요청당 1회만 실행 보장
- `SecurityConfig`: 인증 필요 경로 / 불필요 경로 명확히 분리

---

**2. N+1 문제 발견 및 해결**

**문제 발견**  
게시글 목록 조회 시 쿼리가 비정상적으로 과다 발생하는 것을 직접 확인.  
`@ManyToOne` 기본 fetch 전략(EAGER)으로 인해 게시글 N개 조회 시 작성자 로딩을 위해 N+1번 쿼리 실행.

**해결 과정**

| 방법 | 적용 위치 | 효과 |
|------|----------|------|
| `FetchType.LAZY` | Post.user, Comment.post, PostLike.user/post | 실제 접근 시에만 로딩 |
| `JOIN FETCH` | PostRepository.findPostWithComments | 게시글+댓글 1쿼리로 처리 |
| `findByPostId()` | CommentRepository | Post 사전 조회 제거 |
| `countByPostId()` | PostLikeRepository | Post 사전 조회 제거 |
| `existsBy()` | PostLikeRepository | findBy 대비 의도 명확화 |

**성능 측정 결과 (MySQL 환경, @ParameterizedTest로 자동화 검증)**

| 데이터 수 | 수정 전 쿼리 | 수정 후 쿼리 | 수정 전 응답 | 수정 후 응답 | 개선율 |
|----------|------------|------------|------------|------------|------|
| 10개 | 12개 | 2개 | 86ms | 5ms | 쿼리 83% ↓ / 응답 94% ↓ |
| 100개 | 102개 | 2개 | 75ms | 11ms | 쿼리 98% ↓ / 응답 85% ↓ |
| **1000개** | **1,002개** | **2개** | **353ms** | **23ms** | **쿼리 99.8% ↓ / 응답 93% ↓** |

> 수정 후 쿼리 수는 데이터 규모와 무관하게 항상 2개로 고정.  
> N이 커질수록 N+1은 선형 증가하지만 수정 후는 O(1) 유지.

---

**3. 도메인 중심 패키지 구조 선택**

처음엔 계층형 구조(controller/service/repository)로 시작했으나, 기능 추가 시 어떤 파일이 어디 있는지 파악이 어려워짐. 스스로 문제를 인식하고 도메인 중심 구조로 전환.

```
com.example.community
├── controller/         # HTTP 요청 진입점
├── domain/
│   ├── user/           # 회원 도메인 (Entity, Repository, Service)
│   ├── post/           # 게시글 도메인
│   ├── comment/        # 댓글 도메인
│   └── like/           # 좋아요 도메인
└── global/
    ├── config/         # Security, Swagger 설정
    ├── exception/      # 전역 예외 처리 (ErrorCode + GlobalExceptionHandler)
    ├── jwt/            # JWT 필터 및 토큰 프로바이더
    └── response/       # 공통 응답 포맷 (ApiResponse<T>)
```

각 도메인이 자신의 책임만 갖도록 설계해 응집도와 가독성 개선.

---

**4. 운영/개발 환경 분리**

Spring 프로파일(`dev` / `prod`)로 환경별 설정 분리. 운영 환경에서 `show-sql`, `generate_statistics` 비활성화로 불필요한 오버헤드 제거. DB 접속 정보를 환경변수로 주입해 소스코드에 민감 정보 미포함.

| 파일 | 환경 | 주요 설정 |
|------|------|----------|
| `application.yml` | 공통 | ddl-auto, 기본 프로파일(dev) |
| `application-dev.yml` | 로컬 | localhost DB, show-sql: true |
| `application-prod.yml` | 운영 | 환경변수 DB 주입, 로깅 OFF |

---

#### 🐛 트러블슈팅

**1. Hibernate 1차 캐시로 인한 N+1 미재현**
- 원인: 테스트 데이터가 모두 동일한 user_id를 가져 Hibernate가 첫 조회 후 캐시 히트로 처리
- 해결: 1,000명의 서로 다른 user에 각 1개의 post를 할당해 정확한 측정 환경 구성

**2. Spring Boot 4.x Docker 빌드 오류**
- 원인: Spring Boot 4.x에서 패키지 경로 변경으로 `exclude` 방식 컴파일 오류 발생
- 해결: `UserService`에 `UserDetailsService` 인터페이스 직접 구현, Spring Security가 자동으로 해당 빈 사용

---

#### API 명세

| Method | URI | 설명 | 인증 |
|--------|-----|------|------|
| POST | /api/users/register | 회원가입 | ❌ |
| POST | /api/auth/login | 로그인 (JWT 발급) | ❌ |
| GET | /api/posts | 게시글 목록 | ✅ |
| GET | /api/posts/paged | 게시글 페이징 | ✅ |
| GET | /api/posts/search | 키워드 검색 | ✅ |
| POST | /api/posts | 게시글 작성 | ✅ |
| PUT | /api/posts/{id} | 게시글 수정 | ✅ |
| DELETE | /api/posts/{id} | 게시글 삭제 | ✅ |
| POST | /api/posts/{id}/like | 좋아요 | ✅ |
| POST | /api/comments | 댓글 작성 | ✅ |
| DELETE | /api/comments/{id} | 댓글 삭제 | ✅ |

---

## 🎓 학력

**[학교명] [학과]** 재학/졸업 예정  
- 데이터베이스 수업 팀 프로젝트: 비효율적인 회의 방식 발견 → Notion 회의록 템플릿 직접 제작, 비동기 사전 공유 방식 도입으로 회의 시간 단축

---

## 📌 자기소개

처음 짠 코드가 마음에 들지 않으면 다시 설계하고, 쿼리가 이상하면 원인을 끝까지 찾습니다. N+1 문제를 직접 발견하고 `@ParameterizedTest`로 N=10/100/1000 케이스를 자동화 검증해 수치로 증명한 것처럼, 문제를 발견하면 감으로 끝내지 않고 검증하는 습관을 갖고 있습니다.

아직 실무 경험은 없지만, 스스로 문제를 찾고 구조로 개선하는 태도는 지금도 꾸준히 실천하고 있습니다.
