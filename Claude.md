# Community Board - Claude.md

## 프로젝트 개요
Spring Boot 4.x 기반 커뮤니티 게시판 서비스.
회원 인증부터 게시글 CRUD, 성능 최적화까지 백엔드 전반을 직접 설계하고 구현하는 개인 프로젝트.

## 기술 스택
- Java 17
- Spring Boot 4.0.3
- Spring Security + JWT (Access Token + Refresh Token 이중 토큰 인증)
- Spring Data JPA
- Spring Data Redis
- MySQL 8.0
- Redis 7 (토큰 저장 및 캐싱)
- H2 (테스트)
- Gradle
- Docker & Docker Compose

## 로컬 실행 방법

### 기본 실행 (MySQL 직접 설치)
```bash
# 1. MySQL 8.0 실행 확인
mysql -u root -p

# 2. DB 생성 (최초 1회)
CREATE DATABASE community;

# 3. application.yml 확인
# spring.datasource.url, username, password 확인

# 4. 실행
./gradlew bootRun

# 5. API 문서 확인
# http://localhost:8080/swagger-ui/index.html
```

### Docker Compose 실행 (추천)
```bash
# MySQL + Redis + 애플리케이션 함께 실행
docker compose up --build

# 백그라운드 실행
docker compose up -d

# 중지
docker compose down

# Redis CLI 접속
docker exec -it community-board-redis-1 redis-cli
```

### 테스트 실행
```bash
# 전체 테스트
./gradlew test

# 특정 테스트만
./gradlew test --tests PostControllerTest

# 테스트 결과 확인
open build/reports/tests/test/index.html
```

### 자주 쓰는 명령어
```bash
# 빌드만
./gradlew build -x test

# 클린 빌드
./gradlew clean build

# 의존성 확인
./gradlew dependencies
```

### 트러블슈팅
- **Port 8080 already in use**: `lsof -ti:8080 | xargs kill -9`
- **MySQL 연결 실패**: docker-compose.yml의 포트 확인 (3307:3306)
- **테스트 실패**: test profile이 적용되는지 확인

## 패키지 구조
하이브리드 구조 (도메인 기반 + Controller 분리)
- domain/ → 비즈니스 로직 응집 (user, post, comment, like)
- controller/ → API 엔드포인트 가시성 확보
- global/ → 공통 기능 (config, exception, jwt, response)

**설계 근거:**
- Controller 분리: REST API 전체 구조 파악 용이
- domain 응집: 기능별 코드 집중 관리
- global 공통화: 중복 제거 및 일관성 유지

## 개발 원칙
- Controller는 요청/응답만 담당
- Service는 비즈니스 로직만 담당
- Repository는 데이터 접근만 담당
- 모든 응답은 ApiResponse/ErrorResponse로 통일
- RESTful URI 설계 원칙 준수
- DTO 기반 요청/응답 (엔티티 노출 방지)

## 인증 구조
**Access Token + Refresh Token 이중 토큰 시스템**
- Access Token: 15분 만료, API 인증에 사용
- Refresh Token: 7일 만료, Redis에 저장 (userId 기준)
- Refresh Token Rotation: 토큰 재발급 시 새로운 Refresh Token도 함께 발급
- BCrypt 비밀번호 암호화
- JWT Secret Key 32자 이상 검증 (애플리케이션 시작 시)
- 401 Unauthorized: AuthenticationEntryPoint 커스터마이징
- 403 Forbidden: AccessDeniedHandler (기본)
- CSRF 비활성화 (REST API 특성)

**엔드포인트:**
- `POST /api/auth/login`: Access Token + Refresh Token 발급
- `POST /api/auth/refresh`: Refresh Token으로 새 토큰 재발급
- `POST /api/auth/logout`: Redis에서 Refresh Token 삭제

## 테스트 환경
- Spring Boot 4.x → @SpringBootTest + MockMvcBuilders.webAppContextSetup() 사용
- @MockitoBean 사용 (3.4+부터 @MockBean deprecated)
- apply(springSecurity())로 Security 필터 체인 포함
- CI 환경: build.gradle에 systemProperty 'spring.profiles.active', 'test' 설정

## 완료된 기능

### 인증/인가
- 회원 가입 / 로그인
- **Access Token + Refresh Token 이중 토큰 시스템**
    - Access Token (15분) + Refresh Token (7일)
    - Redis 기반 Refresh Token 저장 (userId 기준, TTL 자동 관리)
    - Refresh Token Rotation (토큰 재사용 방지)
    - `/auth/refresh`: 토큰 재발급
    - `/auth/logout`: Refresh Token 무효화

### 게시글/댓글/좋아요
- 게시글 CRUD (작성, 조회, 수정, 삭제, 조회수)
- 댓글 CRUD
- 좋아요 등록/취소
    - 유니크 제약 조건으로 동시성 문제 해결
    - @Transactional 추가로 원자성 보장
    - JMeter 테스트: 100 동시 요청 → 0 중복 발생

### 성능 최적화
- **N+1 쿼리 문제 해결**
    - FetchType.LAZY + JOIN FETCH 적용
    - 쿼리 수: 1,002개 → 2개 (99.8% 감소)
    - 응답속도: 353ms → 23ms (93% 개선)
- **Redis 캐싱 도입**
    - 좋아요 수 캐싱 (TTL: 10분)
    - CacheConfig로 캐시별 TTL 관리

### 코드 품질
- **단위 변환 책임 분리**
    - JWT(ms) → Redis TTL(sec) 변환을 RefreshToken.of() 내부로 이동
    - AuthService는 Redis 내부 구현을 몰라도 됨
- **중복 코드 제거**
    - saveRefreshToken() private 메서드 추출
    - DRY 원칙 준수
- **메서드 네이밍 개선**
    - refresh() → reissueToken() (의도를 명확하게 표현)

### 예외 처리 및 검증
- JWT 인증 401/403 예외 처리
- Validation 기반 요청 검증
- 전역 예외 처리 (CustomException + ErrorCode)

### 문서화 및 테스트
- Swagger(OpenAPI) API 문서화
- Controller 테스트 (MockMvc 기반)
- Service Layer 단위 테스트
- AuthServiceTest 업데이트 (Refresh Token 검증)

### 인프라
- GitHub Actions CI 구축
- test/prod 환경 분리
- Docker Compose (MySQL + Redis + App)
- 하이브리드 패키지 구조 설계

## 알려진 이슈 및 한계
1. **환경 설정**
    - test/prod 환경만 존재 (dev 환경 별도 없음)
    - application.yml이 기본 설정 역할

2. **테스트 커버리지**
    - Refresh Token 관련 통합 테스트 부족
    - Redis를 사용하는 실제 환경 테스트 필요

## 앞으로 구현할 기능 (우선순위)
1. **통합 테스트 고도화** (높음)
    - TestContainers로 실제 Redis 환경 테스트
    - Refresh Token Rotation 통합 테스트
    - JMeter 부하 테스트 (토큰 갱신 동시성)

2. **QueryDSL 도입** (중)
    - 동적 검색 쿼리 구현
    - 복잡한 조회 조건 처리

3. **모니터링 및 로깅** (중)
    - Redis 캐시 히트율 모니터링
    - Refresh Token 재발급 빈도 추적
    - 성능 메트릭 수집

4. **CI/CD 자동화** (낮음)
    - GitHub Actions CD 구축
    - Docker 이미지 자동 배포

## 학습 목표
- 실무에서 마주할 수 있는 문제들을 직접 경험
- "왜 이렇게 설계했는지" 설명 가능한 프로젝트
- 성능 최적화의 측정 기반 접근
- 동시성 제어 경험
- 테스트 전략 수립

## 주요 구현 결정 사항

### 1. Refresh Token 단위 변환 책임 분리
**문제**: JWT는 milliseconds, Redis TTL은 seconds 단위 사용
```java
// Before: AuthService가 Redis 내부 구현을 알아야 함
refreshTokenRepository.save(
    RefreshToken.of(userId, token, expiration / 1000)
);

// After: RefreshToken이 단위 변환 책임 가짐
public static RefreshToken of(Long userId, String token, Long expirationMillis) {
    return new RefreshToken(userId, token, expirationMillis / 1000);
}
```
**효과**: 단일 책임 원칙 준수, 변경 영향 범위 최소화

### 2. Refresh Token Rotation
**목적**: 토큰 재사용 방지, 보안 강화
**구현**: `/auth/refresh` 호출 시 새로운 Access Token + Refresh Token 모두 발급
**효과**: 토큰 탈취 시 피해 최소화

### 3. 중복 코드 제거
**문제**: login()과 reissueToken()에서 Refresh Token 저장 로직 중복
**해결**: saveRefreshToken() private 메서드 추출
**효과**: DRY 원칙 준수, 유지보수성 향상

### 4. 메서드 네이밍
**변경**: refresh() → reissueToken()
**이유**: 의도를 명확하게 표현 (실무 관점)
