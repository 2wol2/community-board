# Community Board API Server

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.x-brightgreen)
![MySQL](https://img.shields.io/badge/MySQL-8-blue)
![JWT](https://img.shields.io/badge/JWT-Auth-black)
![CI](https://github.com/2wol2/community-board/actions/workflows/ci.yml/badge.svg)

> JWT 인증 기반 커뮤니티 API 서버 프로젝트입니다.

Spring Boot를 공부하며 기본적인 CRUD 기능은 구현할 수 있었지만,
실제 서비스 구조에서 중요한 인증, 예외 처리, 테스트, 환경 분리 경험은 부족하다고 느꼈습니다.

그래서 단순 기능 구현보다
“왜 이렇게 설계했는지 설명 가능한 프로젝트”
를 목표로 JWT 인증, Validation, 테스트 코드, CI 환경까지 직접 구성하며 프로젝트를 진행했습니다.

---

# 📌 Overview

이 프로젝트는 게시글, 댓글, 좋아요 기능을 제공하는 REST API 서버입니다.

단순 CRUD 구현에서 끝나지 않고:

* JWT 기반 인증/인가
* Validation 및 예외 처리
* DTO 기반 응답 구조
* MockMvc 기반 테스트 코드
* GitHub Actions CI
* Docker 기반 실행 환경
* test/prod 환경 분리

등 실제 운영 환경을 고려한 구조를 직접 경험하고 구성했습니다.

---

# 🚀 Key Experiences

- Spring Security + JWT 인증 구조 구현
- MockMvc 기반 Controller 테스트 작성
- GitHub Actions 기반 CI 환경 구성
- N+1 Query 문제 분석 및 성능 개선
- DTO 기반 API 응답 구조 설계
- Spring Boot 4.x 테스트 환경 구성 경험

---

# 🛠 Tech Stack

| Category | Skills                                    |
| -------- | ----------------------------------------- |
| Backend  | Java 17, Spring Boot 4.x, Spring Security |
| Database | MySQL 8, H2                               |
| ORM      | Spring Data JPA (Hibernate)               |
| Auth     | JWT                                       |
| Infra    | Docker, Docker Compose                    |
| Test     | JUnit5, Mockito, MockMvc                  |
| CI       | GitHub Actions                            |
| Docs     | Swagger(OpenAPI)                          |

---

# ✨ Features

## 👤 User

* 회원가입
* 로그인(JWT 발급)
* 사용자 조회

## 📝 Post

* 게시글 CRUD
* 조회수 증가

## 💬 Comment

* 댓글 작성/삭제

## ❤️ Like

* 좋아요 등록/취소
* 중복 좋아요 방지

---

# 🏗 Project Structure

```text
src/main/java/com/example/community
├── domain
│   ├── user
│   ├── post
│   ├── comment
│   └── like
├── global
│   ├── exception
│   ├── jwt
│   └── config
└── controller
```

도메인별(feature 기반) 패키지 구조를 사용하여 관련 기능들을 응집도 있게 관리했습니다.

---

# 🔐 Authentication Flow

JWT 기반 Stateless 인증 방식을 적용했습니다.

```text
[1] Login Request
        ↓
[2] Username / Password 검증
        ↓
[3] JWT Access Token 발급
        ↓
[4] Authorization Header 포함
        ↓
[5] JwtAuthenticationFilter에서 토큰 검증
        ↓
[6] SecurityContext에 인증 정보 저장
        ↓
[7] 인증된 사용자로 API 접근
```

Authorization Header 예시:

```http
Authorization: Bearer {JWT_TOKEN}
```

Stateless 인증 구조를 적용하여 서버 세션 없이 JWT 기반으로 인증을 처리했습니다.

---

# 📦 API Response

일관된 API 응답 구조를 적용했습니다.

## Success Response

```json
{
  "success": true,
  "data": {},
  "message": "요청 성공"
}
```

## Error Response

```json
{
  "code": "POST_NOT_FOUND",
  "message": "게시글이 없습니다."
}
```

---

# 🧪 Test & CI

Service Layer 테스트와 MockMvc 기반 Controller 테스트를 작성했습니다.
또한 GitHub Actions를 통해 push / pull request 시 자동으로 테스트와 빌드가 수행되도록 구성했습니다.

## Test Coverage

| 구분              | 테스트 내용                                |
| --------------- | ------------------------------------- |
| Service Test    | 회원가입, 로그인, 좋아요, 좋아요 취소, 예외 케이스 검증     |
| Controller Test | MockMvc 기반 API 요청/응답 검증               |
| Validation Test | 잘못된 요청 값에 대한 400 응답 검증                |
| Security Test   | JWT 미인증 요청에 대한 401 응답 검증              |
| Exception Test  | CustomException 발생 시 ErrorResponse 검증 |

## CI Pipeline

```text
Push / Pull Request
        ↓
GitHub Actions 실행
        ↓
JDK 17 설정
        ↓
Gradle Test 실행
        ↓
Gradle Build 검증
        ↓
성공 / 실패 결과 확인
```

---

# ⚙ Environment

- test : H2 기반 테스트 환경
- prod : 환경 변수 기반 운영 환경 (MySQL)

---

# 📄 API Docs

Swagger(OpenAPI)를 적용하여 API 명세 및 테스트 환경을 구성했습니다.

```text
http://localhost:8080/swagger-ui/index.html
```

JWT 인증 후 API를 직접 테스트할 수 있도록 구성했습니다.

---

# ⚠ Trouble Shooting

## N+1 Query 문제 해결

### 문제 발견

게시글 목록 조회 시 SQL 로그에서 쿼리가 비정상적으로 과다 발생하는 것을 직접 확인했습니다.
`@ManyToOne` 기본 fetch 전략(EAGER)으로 인해 게시글 N개 조회 시 N+1번 쿼리가 실행되었습니다.

### 해결 방법

* `FetchType.LAZY` 적용
* `JOIN FETCH`로 필요한 경우 한 번에 조회

### 성능 측정 결과

| 데이터 수 | 수정 전 쿼리 | 수정 후 쿼리 | 수정 전 응답 | 수정 후 응답 |
|---|---|---|---|---|
| 10개   | 12개     | 2개      | 86ms    | 5ms     |
| 100개  | 102개    | 2개      | 75ms    | 11ms    |
| 1000개 | 1,002개  | 2개      | 353ms   | 23ms    |

---

## JWT 인증 실패 시 401/403 처리 문제

### 문제

토큰이 없거나 만료되었을 때 403 Forbidden이 반환되었습니다.

### 해결

`authenticationEntryPoint`를 추가하여 인증 실패 시 401 Unauthorized를 반환하도록 수정했습니다.

```java
.exceptionHandling(ex -> ex
    .authenticationEntryPoint((request, response, e) ->
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
    )
)
```

---

## Spring Boot 4.x 테스트 환경 구성

### 문제

Spring Boot 4.x 환경에서 기존 테스트 방식(`@WebMvcTest`, `@MockBean`)이 정상 동작하지 않아 Controller 테스트 환경 구성에 어려움이 있었습니다.

### 해결

`@SpringBootTest` 기반으로 테스트 환경을 구성하고,

```java
MockMvcBuilders.webAppContextSetup(context)
    .apply(springSecurity())
```

방식을 사용하여 JWT/Security 흐름을 포함한 Controller 테스트를 구성했습니다.

또한 `@MockitoBean` 기반 Mock 객체를 적용하여 Controller 계층에 집중할 수 있도록 테스트 범위를 분리했습니다.

---

## CI 환경에서 test profile 미적용 문제

### 문제

GitHub Actions 환경에서 `test profile`이 적용되지 않아 H2 대신 MySQL 연결을 시도하며 테스트가 실패했습니다.

### 해결

```gradle
tasks.named('test') {
    systemProperty 'spring.profiles.active', 'test'
}
```

Gradle test task에 `test profile`을 명시적으로 적용하여 CI 환경에서도 동일한 테스트 환경을 유지하도록 구성했습니다.

---

# 🚀 Improvements

* Refresh Token 기반 인증 구조 개선
* Redis 기반 인증/캐시 관리
* QueryDSL 기반 동적 검색 기능 구현
* Controller/통합 테스트 고도화
* Docker Compose 운영 환경 개선
* 배포 자동화(CD) 구축

---

# ▶ Run

```bash
./gradlew bootRun
```

또는

```bash
docker compose up --build
```

---

# 💡 Design Decisions

- DTO 기반 응답 구조로 민감 정보 노출 방지
- CustomException + ErrorCode 기반 예외 처리 구조 적용
- JWT 인증 실패 시 401 Unauthorized 반환
- test/prod 환경 분리
---

# 📚 What I Learned

이 프로젝트를 통해:

- JWT 인증 흐름 및 Spring Security 구조
- Validation / Exception Handling 구조
- 테스트 환경 분리 및 MockMvc 테스트
- GitHub Actions 기반 CI 환경 구성
- N+1 문제 분석 및 개선

등 실제 서비스 구조와 운영 환경을 직접 경험할 수 있었습니다.

