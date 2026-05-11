#!/bin/bash
# ============================================================
# Clipping MCP Server — 로컬 dev 기동 스크립트
# 사용: ./scripts/dev-start.sh
#
# .env 에서 환경변수를 로드하고, 포트 점유를 체크한 뒤
# bootRun 을 실행한다. 프론트 빌드는 스킵 (이미 되어 있다고 가정).
# ============================================================

set -eu

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$ROOT_DIR"

# .env 파일 존재 확인
if [ ! -f .env ]; then
  echo "[dev-start] .env 파일이 없습니다. 예시 파일을 복사한 뒤 값을 채우세요:"
  echo "    cp .env.example .env"
  echo ""
  echo "  최소 필수 항목 — DB_PASSWORD"
  exit 1
fi

# .env 로드 (주석/빈 줄 무시)
set -a
# shellcheck disable=SC1091
source .env
set +a

# DB_PASSWORD 검증 (서버 기동 전 조기 실패)
if [ -z "${DB_PASSWORD:-}" ]; then
  echo "[dev-start] DB_PASSWORD 가 비어있습니다. .env 에 값을 채워주세요."
  exit 1
fi

# 포트 점유 안내 (kill 하지 않음 — 다른 프로세스 손상 방지)
PRIMARY_PORT=8086
if lsof -i :$PRIMARY_PORT -sTCP:LISTEN >/dev/null 2>&1; then
  echo "[dev-start] 포트 $PRIMARY_PORT 이미 사용 중 — 서버가 8087 로 fallback 됩니다."
  echo "            (Playwright 는 E2E_BASE_URL=http://localhost:8087 로 override 필요)"
fi

echo "[dev-start] bootRun 시작 (프론트 빌드 스킵)…"
exec ./gradlew bootRun -PskipFrontendBuild=true
