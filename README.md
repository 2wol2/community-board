# Community Board API Server

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

- JWT 기반 인증/인가
- Validation 및 예외 처리
- DTO 기반 응답 구조
- MockMvc 기반 테스트 코드
- GitHub Actions CI
- Docker 기반 실행 환경
- dev/test/prod 환경 분리

등 실제 운영 환경을 고려한 구조를 직접 구성했습니다.



# 🛠 Tech Stack

| Category | Skills |
|---|---|
| Backend | Java 17, Spring Boot 4.x, Spring Security |
| Database | MySQL 8, H2 |
| ORM | Spring Data JPA (Hibernate) |
| Auth | JWT |
| Infra | Docker, Docker Compose |
| Test | JUnit5, Mockito, MockMvc |
| CI | GitHub Actions |
| Docs | Swagger(OpenAPI) |



# ✨ Features

## 👤 User
- 회원가입
- 로그인(JWT 발급)

## 📝 Post
- 게시글 CRUD
- 조회수 증가

## 💬 Comment
- 댓글 작성/삭제

## ❤️ Like
- 좋아요 등록/취소
- 중복 좋아요 방지

---

# 프로젝트 구조

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

````markdown
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

Authorization Header 예시:

Authorization: Bearer {JWT_TOKEN}

Stateless 인증 구조를 적용하여 서버 세션 없이 JWT 기반으로 인증을 처리했습니다.

---

# API 응답 구조

## 성공 응답

```json
{
  "success": true,
  "data": {
    "id": 1,
    "title": "게시글"
  },
  "message": "요청 성공"
}
```

## 실패 응답

```json
{
  "code": "POST_NOT_FOUND",
  "message": "게시글이 없습니다."
}
```

---

# Validation

DTO 기반 Validation을 적용했습니다.

예시:

```java
@NotBlank(message = "사용자명은 필수입니다.")
@Size(min = 2, max = 20)
private String username;
```

Validation 실패 시:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "title: 제목은 필수입니다."
}
```

---

# 🧪 Test & CI

Service Layer 테스트와 MockMvc 기반 Controller 테스트를 작성했습니다.  
또한 GitHub Actions를 통해 push / pull request 시 자동으로 테스트와 빌드가 수행되도록 구성했습니다.

## Test Coverage

| 구분 | 테스트 내용 |
|---|---|
| Service Test | 회원가입, 로그인, 좋아요, 좋아요 취소, 예외 케이스 검증 |
| Controller Test | MockMvc 기반 API 요청/응답 검증 |
| Validation Test | 잘못된 요청 값에 대한 400 응답 검증 |
| Security Test | JWT 미인증 요청에 대한 401 응답 검증 |
| Exception Test | CustomException 발생 시 ErrorResponse 검증 |

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

---

# Docker 실행

## MySQL 실행

```bash
docker run --name community-mysql \
-e MYSQL_ROOT_PASSWORD=root1234 \
-e MYSQL_DATABASE=community \
-p 3307:3306 \
-d mysql:8
```

---

# 환경 설정

## application.yml

공통 설정 관리

* JWT 설정
* active profile

## application-dev.yml

개발 환경 설정

* MySQL
* JPA 설정

## application-test.yml

테스트 환경 설정

* H2 Database

## application-prod.yml

운영 환경 설정

* 환경 변수 기반 datasource

---

# Swagger

```text
http://localhost:8080/swagger-ui/index.html
```

Swagger(OpenAPI)를 적용하여 API 명세 및 테스트 환경을 구성했습니다.

JWT 인증 후 API를 직접 테스트할 수 있도록 구성했습니다.

---

# Trouble Shooting

## N+1 문제 해결

**문제 발견**
게시글 목록 조회 시 SQL 로그에서 쿼리가 비정상적으로 과다 발생하는 것을 직접 확인.
`@ManyToOne` 기본 fetch 전략(EAGER)으로 인해 게시글 N개 조회 시 N+1번 쿼리 실행.

**해결 방법**

* `FetchType.LAZY` 적용
* `JOIN FETCH`로 필요한 경우 한 번에 조회

**성능 측정 결과**

| 데이터 수 | 수정 전 쿼리 | 수정 후 쿼리 | 수정 전 응답 | 수정 후 응답 |
| ----- | ------- | ------- | ------- | ------- |
| 10개   | 12개     | 2개      | 86ms    | 5ms     |
| 100개  | 102개    | 2개      | 75ms    | 11ms    |
| 1000개 | 1,002개  | 2개      | 353ms   | 23ms    |

---

## JWT 인증 실패 시 403 반환 문제

### 문제

토큰이 없거나 만료되었을 때 403 Forbidden 반환.

### 해결

`authenticationEntryPoint`를 추가하여 인증 실패 시 401 Unauthorized 반환하도록 수정.

```java
.exceptionHandling(ex -> ex
        .authenticationEntryPoint((request, response, e) ->
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
    )
)
```

---

## Post.viewCount NullPointerException

### 문제

조회수 증가 시 `viewCount++`에서 NullPointerException 발생.

### 원인

`Long viewCount` 초기값이 null 상태.

### 해결

```java
@Builder.Default
private Long viewCount = 0L;
```

---

## application-dev.yml 누락으로 인한 실행 실패

### 문제

`spring.profiles.active=dev` 상태에서 datasource 설정 누락.

### 해결

`application-dev.yml` 분리 및 환경별 설정 구성.

---

## Entity 직접 반환 문제

### 문제

회원가입 응답에서 password 해시값 노출.

### 해결

Entity 대신 DTO 반환 구조로 변경.

Entity를 직접 반환하지 않고 DTO 기반 응답 구조를 적용했습니다.
이를 통해 password와 같은 민감 정보 노출을 방지하고, API 응답 구조를 안정적으로 관리하도록 개선했습니다.

---

## Spring Boot 4.x Controller 테스트 환경 구성

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

## ObjectMapper Bean 주입 문제

### 문제

Spring Boot 4.x 테스트 환경에서 `ObjectMapper` Bean 자동 주입이 실패했습니다.

### 해결

```java
@TestConfiguration
class ControllerTestConfig {

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
```

테스트 전용 Bean 구성을 통해 Controller 테스트 환경을 안정화했습니다.

---

# 개선 예정

* Refresh Token 기반 인증 구조 개선
* Redis 기반 인증/캐시 관리
* QueryDSL 기반 동적 검색 기능 구현
* Controller/통합 테스트 고도화
* Docker Compose 운영 환경 개선
* 배포 자동화(CD) 구축
---

# 실행 방법

```bash
./gradlew bootRun
```

또는

```bash
docker compose up --build
```

---

# 설계 의도

## DTO 기반 응답 구조

Entity를 직접 반환하지 않고 DTO 기반 응답 구조를 적용했습니다.
민감 정보 노출을 방지하고, API 스펙 변경 시 유연하게 대응할 수 있도록 설계했습니다.

## Exception Handling

CustomException + ErrorCode 구조를 적용하여
예외 상황을 일관된 JSON 형태로 반환하도록 구성했습니다.

## JWT 인증 처리

JWT 인증 실패 시 403 대신 401 Unauthorized를 반환하도록 수정하여
HTTP 상태 코드의 의미를 명확하게 구분하도록 개선했습니다.

---

# 회고 / 배운 점

이 프로젝트를 진행하며 단순 CRUD 구현보다,
인증/예외 처리/Validation/테스트 코드 구조 설계가 백엔드 개발에서 중요하다는 점을 경험했습니다.

특히 JWT 인증 흐름과 Spring Security 동작 방식,
Global Exception Handling 구조를 직접 구현하며 백엔드 구조에 대한 이해를 높일 수 있었습니다.

---

# 프로젝트 목표

단순히 동작하는 CRUD가 아니라,

* 왜 이렇게 설계했는지 설명할 수 있는 구조
* 인증/예외/Validation 흐름을 이해한 구조
* 실제 운영 환경을 고려한 구조

를 만드는 것을 목표로 개발했습니다.
