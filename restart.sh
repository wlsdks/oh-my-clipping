#!/bin/bash
# 서버 재시작 스크립트
# 사용법: ./restart.sh

cd "$(dirname "$0")"

# .env 자동 로드 — Spring Boot 는 .env 를 읽지 않으므로 명시적으로 source.
# DB_PASSWORD, CORS_ALLOWED_ORIGINS, DEV_BOOTSTRAP, MAX_LOGIN_ATTEMPTS_PER_MINUTE 등
# 필수 환경변수를 주입한다. `set -a` 로 이후 할당을 자동 export 한다.
if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
  echo "📄 .env loaded"
else
  echo "⚠️  .env 파일이 없습니다 — 서버가 환경변수 부재로 실패할 수 있음"
fi

echo "🔄 기존 서버 종료 중..."
# 기존 서버 종료 (lsof 패턴은 Safari 등 다른 프로세스를 죽일 수 있어 사용 금지)
pkill -f 'bootRun' 2>/dev/null || true
sleep 2

echo "🚀 서버 시작 중..."
# DART_API_KEY must be set in environment before running
./gradlew bootRun -PskipFrontendBuild=true 2>&1 &
SERVER_PID=$!

# 시작 대기 (최대 30초)
for i in $(seq 1 30); do
  if curl -s -o /dev/null -w "%{http_code}" http://localhost:8086/ 2>/dev/null | grep -q "200\|302"; then
    echo ""
    echo "✅ 서버 시작 완료! (PID: $SERVER_PID)"
    echo "   관리자: http://localhost:8086/admin/login"
    echo "   사용자: http://localhost:8086/user/login"
    echo "   계정: dev.admin@clipping.local / LocalPass123! (관리자)"
    echo "         dev.user@clipping.local / LocalPass123! (사용자)"
    echo "   * DEV_BOOTSTRAP=true 로 시드된 로컬 전용 계정"
    exit 0
  fi
  printf "."
  sleep 1
done

echo ""
echo "⚠️ 서버가 30초 내에 시작되지 않았습니다. 로그를 확인하세요."
