#!/bin/bash

# 성능 테스트용 데이터 생성 스크립트

set -e

BASE_URL="http://localhost:8080"
TOTAL_POSTS=${1:-100}  # 첫 번째 인자로 게시글 개수 받기 (기본 100)

echo "=== 성능 테스트 데이터 생성 시작 ==="

# 1. 회원가입
echo "1. 테스트 사용자 생성 중..."
SIGNUP_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/signup" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123"
  }')

echo "회원가입 응답: $SIGNUP_RESPONSE"

# 2. 로그인
echo "2. 로그인 중..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }')

# accessToken 추출 (jq 사용)
if command -v jq &> /dev/null; then
    TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.data.accessToken')
else
    # jq 없으면 grep으로 추출
    TOKEN=$(echo $LOGIN_RESPONSE | grep -o '"accessToken":"[^"]*' | grep -o '[^"]*$')
fi

echo "토큰: ${TOKEN:0:20}..."

# 3. 게시글 생성
echo "3. 게시글 $TOTAL_POSTS개 생성 중..."
for i in $(seq 1 $TOTAL_POSTS)
do
  curl -s -X POST "$BASE_URL/api/posts" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"title\": \"테스트 게시글 $i\",
      \"content\": \"테스트 내용 $i\"
    }" > /dev/null

  if [ $((i % 10)) -eq 0 ]; then
    echo "  $i개 생성 완료..."
  fi
done

echo "게시글 생성 완료!"

# 4. 랜덤 좋아요 및 조회수 생성
echo "4. 좋아요 및 조회수 생성 중..."
for post_id in $(seq 1 $TOTAL_POSTS)
do
  # 랜덤 좋아요 (0~30개)
  likes=$((RANDOM % 31))

  # 좋아요 등록 (첫 번째만 성공, 나머지는 중복 에러)
  if [ $likes -gt 0 ]; then
    curl -s -X POST "$BASE_URL/api/posts/$post_id/like" \
      -H "Authorization: Bearer $TOKEN" > /dev/null
  fi

  # 랜덤 조회수 (0~100회)
  views=$((RANDOM % 101))
  for j in $(seq 1 $views)
  do
    curl -s "$BASE_URL/api/posts/$post_id" > /dev/null
  done

  if [ $((post_id % 10)) -eq 0 ]; then
    echo "  $post_id번 게시글: 좋아요 $likes, 조회수 $views"
  fi
done

echo ""
echo "=== 데이터 생성 완료! ==="
echo "총 게시글: $TOTAL_POSTS개"
echo ""

# 5. Redis 랭킹 확인
echo "5. Redis 랭킹 확인 중..."
echo "Top 10 랭킹:"
docker exec community-board-redis-1 redis-cli ZREVRANGE post:ranking 0 9 WITHSCORES

echo ""
echo "=== 성능 테스트 준비 완료 ==="
echo ""
echo "다음 명령어로 성능 측정을 시작하세요:"
echo "  ab -n 1000 -c 100 http://localhost:8080/api/posts/ranking?topN=10"
echo "  ab -n 1000 -c 100 http://localhost:8080/api/posts/ranking/db?topN=10"
