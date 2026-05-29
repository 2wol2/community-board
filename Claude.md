# Community Board - Claude.md

## 프로젝트 개요
Spring Boot 4.x 기반 커뮤니티 게시판 서비스.
회원 인증부터 게시글 CRUD, 성능 최적화까지 백엔드 전반을 직접 설계하고 구현하는 개인 프로젝트.

## 기술 스택
- Java 17
- Spring Boot 4.0.3
- Spring Security + JWT (Access Token 기반 Stateless 인증)
- Spring Data JPA
- MySQL 8.0
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
# MySQL + 애플리케이션 함께 실행
docker compose up --build

# 백그라운드 실행
docker compose up -d

# 중지
docker compose down
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
- Access Token 기반 Stateless 인증 (만료시간: 1시간)
- BCrypt 비밀번호 암호화
- JWT Secret Key 32자 이상 검증 (애플리케이션 시작 시)
- 401 Unauthorized: AuthenticationEntryPoint 커스터마이징
- 403 Forbidden: AccessDeniedHandler (기본)
- CSRF 비활성화 (REST API 특성)

## 테스트 환경
- Spring Boot 4.x → @SpringBootTest + MockMvcBuilders.webAppContextSetup() 사용
- @MockitoBean 사용 (3.4+부터 @MockBean deprecated)
- apply(springSecurity())로 Security 필터 체인 포함
- CI 환경: build.gradle에 systemProperty 'spring.profiles.active', 'test' 설정

## 완료된 기능
- 회원 가입 / 로그인 (JWT Access Token 발급)
- 게시글 CRUD (작성, 조회, 수정, 삭제, 조회수)
- 댓글 CRUD
- 좋아요 등록/취소 (중복 방지)
- N+1 쿼리 문제 해결
    - FetchType.LAZY + JOIN FETCH 적용
    - 쿼리 수: 1,002개 → 2개 (99.8% 감소)
    - 응답속도: 353ms → 23ms (93% 개선)
- JWT 인증 401/403 예외 처리
- Validation 기반 요청 검증
- 전역 예외 처리 (CustomException + ErrorCode)
- Swagger(OpenAPI) API 문서화
- 하이브리드 패키지 구조 설계
- Controller 테스트 (MockMvc 기반)
- Service Layer 단위 테스트
- GitHub Actions CI 구축
- test/prod 환경 분리
- Docker Compose 기본 구성

## 알려진 이슈 및 한계
1. **좋아요 동시성 문제**
    - 현재: @Transactional 누락, Race Condition 가능
    - 증상: 동시 요청 시 중복 좋아요 저장될 수 있음

2. **Refresh Token 미구현**
    - 현재: Access Token만 사용 (1시간 만료)
    - 한계: 만료 시 재로그인 필요

3. **캐싱 미적용**
    - 현재: 좋아요 수 조회 시 매번 COUNT 쿼리
    - 영향: 조회 빈번한 게시글은 DB 부하

4. **환경 설정**
    - test/prod 환경만 존재 (dev 환경 별도 없음)
    - application.yml이 기본 설정 역할

## 앞으로 구현할 기능 (우선순위)
1. **좋아요 동시성 제어** (높음)
    - 유니크 제약 조건 또는 낙관적/비관적 락 적용
    - 부하 테스트로 검증

2. **Redis 캐싱 도입** (높음)
    - 좋아요 수 캐싱
    - 성능 측정 및 개선 수치화

3. **Refresh Token 구현** (중)
    - Access Token (15분) + Refresh Token (7일)
    - Redis 기반 토큰 저장/관리

4. **QueryDSL 도입** (중)
    - 동적 검색 쿼리 구현
    - 복잡한 조회 조건 처리

5. **통합 테스트 고도화** (중)
    - TestContainers로 실제 DB 테스트
    - JMeter 부하 테스트

6. **CI/CD 자동화** (낮음)
    - GitHub Actions CD 구축
    - Docker 이미지 자동 배포

## 학습 목표
- 실무에서 마주할 수 있는 문제들을 직접 경험
- "왜 이렇게 설계했는지" 설명 가능한 프로젝트
- 성능 최적화의 측정 기반 접근
- 동시성 제어 경험
- 테스트 전략 수립
