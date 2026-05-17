# Community Board API Server

> Spring Boot 기반 JWT 인증 커뮤니티 API 서버 프로젝트입니다.
> 단순 CRUD 구현을 넘어 인증, 예외 처리, Validation, Docker 환경 구성, 테스트 코드 작성까지 경험하는 것을 목표로 개발했습니다.

---

# 프로젝트 소개

이 프로젝트는 게시글, 댓글, 좋아요 기능을 제공하는 REST API 서버입니다.

Spring Security + JWT 기반 인증 구조를 적용하였고,
실제 운영 환경을 고려하여 다음과 같은 부분들을 개선했습니다.

* JWT 인증 및 권한 처리
* DTO 기반 응답 구조 적용
* Global Exception Handling
* Validation 처리
* Docker 기반 MySQL 환경 구성
* 테스트 코드 작성
* application-dev / test / prod 환경 분리

---

# 기술 스택

## Backend

* Java 17
* Spring Boot
* Spring Security
* Spring Data JPA (Hibernate)
* JWT (jjwt)

## Database

* MySQL 8
* H2 Database (Test)

## DevOps / Infra

* Docker
* Docker Compose

## Test

* JUnit5
* Mockito

## Documentation

* Swagger (springdoc-openapi)

---

# 주요 기능

## 사용자

* 회원가입
* 로그인 (JWT 발급)
* 사용자 조회

## 게시글

* 게시글 생성
* 게시글 조회
* 게시글 수정
* 게시글 삭제
* 조회수 증가

## 댓글

* 댓글 작성
* 댓글 삭제

## 좋아요

* 게시글 좋아요
* 좋아요 취소
* 중복 좋아요 방지

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

# 인증 구조

JWT 기반 Stateless 인증 방식을 적용했습니다.

```text
로그인
→ JWT 발급
→ Authorization Header
→ JwtAuthenticationFilter
→ SecurityContext 저장
→ 인증 사용자 접근
```

Authorization Header 예시:

```http
Authorization: Bearer {JWT_TOKEN}
```

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

# 테스트

Mockito 기반 Service Layer 테스트를 작성했습니다.

테스트 항목:

* 회원가입 성공/실패
* 로그인 성공/실패
* 게시글 좋아요
* 중복 좋아요 예외
* 좋아요 취소
* 사용자 조회 예외

공통 Fixture 클래스를 분리하여 중복 테스트 코드를 제거했습니다.

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

Swagger에서 JWT 인증 후 API 테스트가 가능합니다.

---

# Trouble Shooting

## N+1 문제 해결

**문제 발견**
게시글 목록 조회 시 SQL 로그에서 쿼리가 비정상적으로 과다 발생하는 것을 직접 확인.
`@ManyToOne` 기본 fetch 전략(EAGER)으로 인해 게시글 N개 조회 시 N+1번 쿼리 실행.

**해결 방법**
- `FetchType.LAZY` 적용
- `JOIN FETCH`로 필요한 경우 한 번에 조회

**성능 측정 결과**

| 데이터 수 | 수정 전 쿼리 | 수정 후 쿼리 | 수정 전 응답 | 수정 후 응답 |
|----------|------------|------------|------------|------------|
| 10개 | 12개 | 2개 | 86ms | 5ms |
| 100개 | 102개 | 2개 | 75ms | 11ms |
| 1000개 | 1,002개 | 2개 | 353ms | 23ms |

> 수정 후 쿼리 수는 데이터 규모와 무관하게 항상 2개로 고정.
GitHub에서 Trouble Shooting 섹션 들어가서 맨 위에 붙여넣고 Commit changes 눌러줘 😊Sonnet 4.6

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

# 개선 예정

* Refresh Token 적용
* Redis 기반 인증 관리
* QueryDSL 적용
* Controller 테스트 추가
* Docker Compose 최적화
* CI/CD 구축

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
