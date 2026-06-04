# DevMate - 개발 스터디 모집 플랫폼

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.3-brightgreen)
![MySQL](https://img.shields.io/badge/MySQL-8-blue)
![Redis](https://img.shields.io/badge/Redis-7-red)
![CI](https://github.com/2wol2/community-board/actions/workflows/ci.yml/badge.svg)

> **"개발자들이 함께 성장할 스터디를 찾고, 실시간 인기 스터디를 발견하는 플랫폼"**

📋 **Portfolio**: [PORTFOLIO_COMPACT.md](./PORTFOLIO_COMPACT.md) (면접용 요약본)
📚 **Technical Details**: [PROBLEM_SOLVING.md](./PROBLEM_SOLVING.md) | [ARCHITECTURE.md](./ARCHITECTURE.md)

---

## 💡 왜 이 프로젝트를 만들었나요?

### 1. **실시간 인기 스터디 발견**
- **문제**: 수백 개의 스터디 중 어떤 것이 인기 있는지 알기 어렵다
- **해결**: Redis Sorted Set을 활용한 실시간 랭킹 시스템
- **기술 선택 이유**:
  - 좋아요/조회수 기반 점수 계산 (좋아요 x10 + 조회수 x1)
  - 이벤트 기반 비동기 점수 업데이트로 DB 부하 최소화
  - O(log N) 시간복잡도로 빠른 Top N 조회

### 2. **인기 스터디의 좋아요 부하 처리**
- **문제**: 인기 스터디 조회 시 좋아요 수 조회로 DB 부하 증가
- **해결**: Redis 캐싱 (TTL 10분)
- **성과**: 캐시 히트 시 DB 쿼리 0회

### 3. **좋아요 중복 방지 (동시성 제어)**
- **문제**: 동시에 100명이 좋아요 클릭 시 중복 발생 가능
- **해결**: 유니크 제약 조건 + @Transactional
- **성과**: JMeter 테스트 결과 중복 0건 발생

### 4. **N+1 쿼리 문제**
- **문제**: 게시글 목록 조회 시 1,002개 쿼리 발생
- **해결**: FetchType.LAZY + JOIN FETCH
- **성과**: 99.8% 감소 (1,002개 → 2개), 응답속도 93% 개선 (353ms → 23ms)

---

## 🎯 주요 기능

### 스터디 모집
- 📝 **스터디 모집글 작성** (카테고리, 모집 인원, 마감일)
- 🔍 **검색 & 필터링** (제목/내용, 카테고리, 모집 상태)
- 📊 **실시간 인기 스터디 랭킹** (Redis Sorted Set)
- ⏰ **자동 마감** (스프링 스케줄러, 매일 자정 실행)

### 지원 시스템
- ✋ **스터디 지원하기** (지원 메시지 작성)
- ✅ **지원 수락/거절** (스터디장 전용)
- 📋 **지원 목록 관리** (내가 지원한 스터디, 받은 지원)

### 인증 & 보안
- 🔐 **JWT 이중 토큰** (Access Token + Refresh Token)
- 🔄 **Refresh Token Rotation** (토큰 재사용 방지)
- 🗄️ **Redis 기반 토큰 관리** (TTL 자동 만료)

### 성능 최적화
- ⚡ **N+1 쿼리 해결** (99.8% 개선)
- 💾 **Redis 캐싱** (좋아요 수, Refresh Token)
- 🔒 **동시성 제어** (좋아요 중복 방지)
- 📈 **이벤트 기반 비동기 처리** (랭킹 업데이트)

---

## 🛠 기술 스택

**Backend**
- Java 17
- Spring Boot 4.0.3
- Spring Security + JWT
- Spring Data JPA
- Spring Data Redis
- Spring Scheduler

**Database**
- MySQL 8.0 (운영 DB)
- Redis 7 (캐시, 랭킹, 토큰 저장소)
- H2 (테스트)

**DevOps**
- Docker & Docker Compose
- GitHub Actions (CI)
- Gradle

**Testing**
- JUnit 5
- Mockito
- JMeter (부하 테스트)

---

## 🚀 빠른 시작

### Docker Compose 실행 (권장)

```bash
# MySQL + Redis + 애플리케이션 함께 실행
docker compose up --build

# API 문서 확인
open http://localhost:8080/swagger-ui/index.html
```

### 로컬 실행

```bash
# 1. MySQL 8.0 실행 및 DB 생성
CREATE DATABASE community;

# 2. Redis 실행
docker run -d -p 6379:6379 redis:7-alpine

# 3. 환경 변수 설정 (application.yml 또는 .env)
JWT_SECRET=your-secret-key-at-least-32-characters-long
JWT_ACCESS_EXPIRATION=900000      # 15분
JWT_REFRESH_EXPIRATION=604800000  # 7일

# 4. 애플리케이션 실행
./gradlew bootRun
```

---

## 📡 API 문서

**Swagger UI**: http://localhost:8080/swagger-ui/index.html

### 주요 엔드포인트

**인증**
- `POST /api/auth/login` - 로그인 (Access Token + Refresh Token 발급)
- `POST /api/auth/refresh` - 토큰 재발급
- `POST /api/auth/logout` - 로그아웃 (Redis에서 Refresh Token 삭제)

**스터디 모집**
- `GET /api/posts/studies/search` - 스터디 검색 (카테고리, 모집 상태 필터)
- `POST /api/posts` - 모집글 작성
- `GET /api/posts/ranking` - 인기 스터디 랭킹 (Redis)

**지원 관리**
- `POST /api/posts/{id}/apply` - 스터디 지원
- `GET /api/posts/{id}/applications` - 지원 목록 조회 (스터디장 전용)
- `POST /api/applications/{id}/accept` - 지원 수락
- `POST /api/applications/{id}/reject` - 지원 거절

**좋아요**
- `POST /api/posts/{id}/like` - 좋아요
- `DELETE /api/posts/{id}/like` - 좋아요 취소
- `GET /api/posts/{id}/likes` - 좋아요 수 조회 (Redis 캐싱)

---

## 🏗 프로젝트 구조

```
src/main/java/com/community/board/
├── controller/          # API 엔드포인트
│   ├── PostController
│   ├── ApplicationController
│   └── AuthController
├── domain/             # 도메인별 응집
│   ├── user/
│   │   ├── User, UserRepository, UserService
│   │   └── AuthService (인증 로직)
│   ├── post/
│   │   ├── Post, PostRepository, PostService
│   │   ├── ranking/      # Redis 랭킹 시스템
│   │   ├── event/        # 비동기 이벤트 처리
│   │   └── scheduler/    # 자동 마감 스케줄러
│   ├── application/      # 지원 시스템
│   └── like/             # 좋아요 (동시성 제어)
└── global/             # 공통 기능
    ├── config/         # Security, JPA, Redis, Cache
    ├── jwt/            # JWT 토큰 처리
    ├── exception/      # 예외 처리
    └── response/       # 공통 응답 형식
```

**설계 근거**: Controller 분리 (API 가시성) + Domain 응집 (기능별 관리) + Global 공통화 (중복 제거)

---

## 📊 성능 최적화 결과

**N+1 쿼리 해결**
- 쿼리 수: 1,002개 → 2개 (99.8% 감소)
- 응답 속도: 353ms → 23ms (93% 개선)

**Redis 랭킹 시스템** (부하 테스트: 1,000 요청 / 100 동시 접속)
- 100개 게시글: Redis 164ms vs DB 252ms (35% 개선)
- 1,000개 게시글: Redis 130ms vs DB 761ms (83% 개선)
- 처리량: Redis 772 req/s vs DB 131 req/s (5.9배)

**동시성 제어**
- JMeter 100명 동시 좋아요: 중복 0건 (유니크 제약 + @Transactional)

---

## 🧪 테스트

```bash
# 전체 테스트
./gradlew test

# 테스트 리포트
open build/reports/tests/test/index.html

# 특정 테스트만
./gradlew test --tests PostLikeServiceTest
```

---

## ⚙️ 환경 설정

### 환경별 프로파일
- `application.yml`: 기본 설정
- `application-prod.yml`: 운영 환경
- `application-test.yml`: 테스트 환경 (H2)

### 환경 변수

```bash
# JWT 설정
JWT_SECRET=your-secret-key-at-least-32-characters-long
JWT_ACCESS_EXPIRATION=900000       # 15분
JWT_REFRESH_EXPIRATION=604800000   # 7일

# MySQL
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/community
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=password

# Redis
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379
```

---

## 🔧 트러블슈팅

**Port 8080 already in use**
```bash
lsof -ti:8080 | xargs kill -9
```

**MySQL 연결 실패**
- docker-compose.yml의 포트 확인 (3307:3306)
- MySQL 실행 상태: `docker ps`

**Redis 연결 실패**
```bash
docker ps | grep redis
docker exec -it community-board-redis-1 redis-cli
> ping
PONG
```

---

## 🎓 학습 내용 및 성과

### 기술적 깊이
- **Redis 활용**: Sorted Set (랭킹), String (캐시), Hash (Refresh Token)
- **동시성 제어**: 유니크 제약 조건, @Transactional, 낙관적 락 개념
- **성능 최적화**: N+1 쿼리 해결, 캐시 전략, 이벤트 기반 비동기 처리
- **Spring 스케줄러**: Cron 표현식, @Transactional과의 상호작용

### 실무 경험
- **도메인 로직 복잡도**: 단순 CRUD를 넘어 지원 플로우, 권한 관리
- **상태 관리**: RecruitStatus, ApplicationStatus의 생명주기
- **비즈니스 요구사항**: "스터디장만 지원자를 볼 수 있다", "자기 스터디에는 지원 불가"

### 설계 의사결정
- **단일 책임 vs 빠른 구현**: Post 엔티티에 스터디 필드 포함 (트레이드오프 인식)
- **동기 vs 비동기**: 랭킹 업데이트는 비동기, 좋아요 등록은 동기 (이유 설명 가능)
- **캐시 TTL 전략**: 좋아요 수는 10분, Refresh Token은 7일 (목적에 따라 다름)

---

## 📝 라이선스

MIT License

---

## 📧 Contact

GitHub: [@yourusername](https://github.com/yourusername)

---

> 💡 **면접 준비 TIP**: 이 프로젝트의 모든 기술 선택에는 "왜?"라는 질문에 답할 수 있는 근거가 있습니다.
> 예: "Redis를 쓴 이유? → 실시간 랭킹 O(log N), 좋아요 캐싱, Refresh Token 만료 관리"
