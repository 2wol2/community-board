# Redis Sorted Set 랭킹 성능 측정

## 목적
DB 방식 vs Redis Sorted Set 방식의 인기 게시글 조회 성능 비교

## 테스트 환경
- 게시글 수: 100개 / 1,000개 / 10,000개
- 동시 사용자: 10 / 50 / 100
- 측정 항목: 평균 응답 시간, TPS, 에러율

## 테스트 시나리오

### 1. 준비 단계 (수동)

#### 1-1. 애플리케이션 실행
```bash
docker compose up -d
./gradlew bootRun
```

#### 1-2. 회원가입 및 로그인
```bash
# 회원가입
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123"
  }'

# 로그인 (토큰 발급)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }'

# 결과에서 accessToken 복사
export TOKEN="여기에_토큰_붙여넣기"
```

#### 1-3. 테스트 데이터 생성 (100개)
```bash
# 게시글 100개 생성
for i in {1..100}
do
  curl -X POST http://localhost:8080/api/posts \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"title\": \"테스트 게시글 $i\",
      \"content\": \"테스트 내용 $i\"
    }"
  echo "게시글 $i 생성 완료"
  sleep 0.1
done
```

#### 1-4. 랜덤 좋아요 및 조회수 생성
```bash
# 게시글별 랜덤 좋아요 (1~50개)
for post_id in {1..100}
do
  # 좋아요 등록 (랭킹 이벤트 발행)
  likes=$((RANDOM % 50 + 1))
  for j in $(seq 1 $likes)
  do
    curl -X POST http://localhost:8080/api/posts/$post_id/like \
      -H "Authorization: Bearer $TOKEN" \
      2>/dev/null
  done

  # 조회수 증가 (랭킹 이벤트 발행)
  views=$((RANDOM % 100 + 1))
  for k in $(seq 1 $views)
  do
    curl http://localhost:8080/api/posts/$post_id 2>/dev/null
  done

  echo "게시글 $post_id: 좋아요 $likes, 조회수 $views"
done
```

#### 1-5. Redis 랭킹 확인
```bash
# Redis 접속
docker exec -it community-board-redis-1 redis-cli

# Top 10 조회
127.0.0.1:6379> ZREVRANGE post:ranking 0 9 WITHSCORES

# 전체 개수 확인
127.0.0.1:6379> ZCARD post:ranking
```

### 2. 성능 측정 (ApacheBench)

#### 2-1. Redis 방식 측정
```bash
# 10 동시 사용자, 1000 요청
ab -n 1000 -c 10 \
  http://localhost:8080/api/posts/ranking?topN=10

# 50 동시 사용자, 1000 요청
ab -n 1000 -c 50 \
  http://localhost:8080/api/posts/ranking?topN=10

# 100 동시 사용자, 1000 요청
ab -n 1000 -c 100 \
  http://localhost:8080/api/posts/ranking?topN=10
```

#### 2-2. DB 방식 측정
```bash
# 10 동시 사용자, 1000 요청
ab -n 1000 -c 10 \
  http://localhost:8080/api/posts/ranking/db?topN=10

# 50 동시 사용자, 1000 요청
ab -n 1000 -c 50 \
  http://localhost:8080/api/posts/ranking/db?topN=10

# 100 동시 사용자, 1000 요청
ab -n 1000 -c 100 \
  http://localhost:8080/api/posts/ranking/db?topN=10
```

### 3. 결과 수집

각 측정 후 아래 항목 기록:
- Time per request (평균 응답 시간)
- Requests per second (TPS)
- Transfer rate
- Failed requests

### 4. 예상 결과

| 방식 | 게시글 수 | 동시 사용자 | 평균 응답 시간 | TPS |
|------|-----------|-------------|----------------|-----|
| Redis | 100 | 10 | ?ms | ?req/s |
| DB | 100 | 10 | ?ms | ?req/s |
| Redis | 100 | 100 | ?ms | ?req/s |
| DB | 100 | 100 | ?ms | ?req/s |

## JMeter 테스트 플랜 (선택)

1. Thread Group 설정
   - Number of Threads: 10, 50, 100
   - Ramp-up Period: 1초
   - Loop Count: 100

2. HTTP Request Sampler
   - Server: localhost
   - Port: 8080
   - Path: /api/posts/ranking?topN=10

3. Listeners 추가
   - View Results Tree
   - Summary Report
   - Aggregate Report

## 측정 시 주의사항

1. **워밍업**: 첫 3-5번은 워밍업으로 제외
2. **Redis 캐시 초기화**: 매 테스트마다 `FLUSHDB` 수행 후 재측정
3. **애플리케이션 재시작**: DB 방식 측정 전 애플리케이션 재시작
4. **로그 레벨**: 성능 측정 시 로그 레벨을 WARN으로 설정

## 포트폴리오 작성용 템플릿

### 측정 결과

**테스트 환경**
- 게시글: 100개
- 동시 요청: 100개

**Redis 방식**
- 평균 응답 시간: Xms
- TPS: Xreq/s
- 실패율: 0%

**DB 방식**
- 평균 응답 시간: Yms
- TPS: Yreq/s
- 실패율: 0%

**개선 효과**
- 응답 속도: Y → X (Z% 개선)
- 처리량: Y → X (N배 증가)
