#!/bin/bash

# Redis vs DB 성능 비교 벤치마크 스크립트

set -e

TOTAL_POSTS=$1
BASE_URL="http://localhost:8080"

if [ -z "$TOTAL_POSTS" ]; then
    echo "사용법: $0 <게시글_개수>"
    echo "예시: $0 100"
    exit 1
fi

echo ""
echo "========================================="
echo "성능 측정: 게시글 ${TOTAL_POSTS}개"
echo "========================================="
echo ""

# 1. 기존 데이터 삭제 (Docker 재시작)
echo "1. 환경 초기화 중..."
docker compose down
docker compose up -d
sleep 10  # 애플리케이션 시작 대기

echo "   완료!"
echo ""

# 2. 테스트 데이터 생성
echo "2. 테스트 데이터 생성 중 (${TOTAL_POSTS}개)..."
./scripts/create-test-data.sh $TOTAL_POSTS

echo ""
echo "3. 워밍업 중..."
# 워밍업 (캐시, JIT 컴파일 등)
for i in {1..10}; do
    curl -s "$BASE_URL/api/posts/ranking?topN=10" > /dev/null
    curl -s "$BASE_URL/api/posts/ranking/db?topN=10" > /dev/null
done
echo "   완료!"

echo ""
echo "========================================="
echo "성능 측정 시작"
echo "========================================="

# 4. Redis 방식 측정
echo ""
echo "[Redis 방식] 측정 중..."
ab -n 1000 -c 100 -q "$BASE_URL/api/posts/ranking?topN=10" > /tmp/redis_result.txt 2>&1

REDIS_TIME=$(grep "Time per request" /tmp/redis_result.txt | head -1 | awk '{print $4}')
REDIS_TPS=$(grep "Requests per second" /tmp/redis_result.txt | awk '{print $4}')
REDIS_FAILED=$(grep "Failed requests" /tmp/redis_result.txt | awk '{print $3}')

echo "  평균 응답 시간: ${REDIS_TIME}ms"
echo "  처리량: ${REDIS_TPS} req/s"
echo "  실패 요청: ${REDIS_FAILED}건"

# 5. DB 방식 측정
echo ""
echo "[DB 방식] 측정 중..."
ab -n 1000 -c 100 -q "$BASE_URL/api/posts/ranking/db?topN=10" > /tmp/db_result.txt 2>&1

DB_TIME=$(grep "Time per request" /tmp/db_result.txt | head -1 | awk '{print $4}')
DB_TPS=$(grep "Requests per second" /tmp/db_result.txt | awk '{print $4}')
DB_FAILED=$(grep "Failed requests" /tmp/db_result.txt | awk '{print $3}')

echo "  평균 응답 시간: ${DB_TIME}ms"
echo "  처리량: ${DB_TPS} req/s"
echo "  실패 요청: ${DB_FAILED}건"

# 6. 결과 비교
echo ""
echo "========================================="
echo "결과 비교"
echo "========================================="
echo ""
echo "게시글 수: ${TOTAL_POSTS}개"
echo ""
echo "| 방식 | 평균 응답시간 | 처리량 (TPS) | 실패 요청 |"
echo "|------|---------------|--------------|-----------|"
echo "| Redis | ${REDIS_TIME}ms | ${REDIS_TPS} req/s | ${REDIS_FAILED}건 |"
echo "| DB | ${DB_TIME}ms | ${DB_TPS} req/s | ${DB_FAILED}건 |"
echo ""

# 개선율 계산 (awk 사용)
IMPROVEMENT=$(awk "BEGIN {printf \"%.1f\", (($DB_TIME - $REDIS_TIME) / $DB_TIME) * 100}")
SPEEDUP=$(awk "BEGIN {printf \"%.1f\", $DB_TIME / $REDIS_TIME}")

echo "개선 효과:"
echo "  응답 속도: ${DB_TIME}ms → ${REDIS_TIME}ms (${IMPROVEMENT}% 개선)"
echo "  처리량: ${SPEEDUP}배 증가"
echo ""

# 7. 결과 파일 저장
RESULT_FILE="performance_result_${TOTAL_POSTS}.txt"
cat > $RESULT_FILE << EOF
========================================
성능 측정 결과: 게시글 ${TOTAL_POSTS}개
측정 일시: $(date)
========================================

[Redis 방식]
  평균 응답 시간: ${REDIS_TIME}ms
  처리량: ${REDIS_TPS} req/s
  실패 요청: ${REDIS_FAILED}건

[DB 방식]
  평균 응답 시간: ${DB_TIME}ms
  처리량: ${DB_TPS} req/s
  실패 요청: ${DB_FAILED}건

[개선 효과]
  응답 속도: ${DB_TIME}ms → ${REDIS_TIME}ms (${IMPROVEMENT}% 개선)
  처리량: ${SPEEDUP}배 증가

========================================
상세 결과
========================================

[Redis 상세]
$(cat /tmp/redis_result.txt)

[DB 상세]
$(cat /tmp/db_result.txt)
EOF

echo "결과가 ${RESULT_FILE}에 저장되었습니다."
echo ""
