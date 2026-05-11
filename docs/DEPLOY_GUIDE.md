# 서버/웹 재시작 가이드

코드 변경 후 반영이 필요할 때 사용하는 가이드입니다.

## 선행조건

- Docker Desktop 실행 중 (PostgreSQL 컨테이너 필요)
- Java 21, pnpm 설치됨

## 1. 프론트엔드만 변경한 경우

```bash
# 1) 프론트 빌드
cd frontend
pnpm build

# 2) 서버 재시작 (프론트 빌드 스킵)
cd ..
pkill -f 'bootRun'
./gradlew bootRun -PskipFrontendBuild=true
```

`src/main/resources/static/**`는 Vite generated 산출물이다. 직접 수정하거나 커밋 대상으로 취급하지 말고 `frontend/` 소스를 수정한 뒤 다시 빌드한다.

## 2. 백엔드만 변경한 경우

```bash
pkill -f 'bootRun'
./gradlew bootRun -PskipFrontendBuild=true
```

## 3. 둘 다 변경한 경우

```bash
pkill -f 'bootRun'
./gradlew bootRun
```

> `./gradlew bootRun`은 프론트 빌드를 자동으로 포함합니다.

## 4. 서버 종료

```bash
# 안전한 방법
pkill -f 'bootRun'

# 또는
pkill -f 'clipping-mcp-server'
```

> **절대 금지**: `lsof -ti:8086 | xargs kill` — 같은 포트를 쓰는 다른 프로세스가 같이 죽습니다.

## 5. DB 시작 (Docker)

```bash
# PostgreSQL 컨테이너 확인
docker ps --filter "name=clipping-postgres"

# 컨테이너가 없거나 꺼져있으면
docker start clipping-postgres
```

## 6. 브라우저 캐시

서버 재시작 후에도 변경이 안 보이면 **Cmd+Shift+R** (하드 리프레시)로 브라우저 캐시를 비우세요.

## 7. 헬스 체크

```bash
curl http://localhost:8086/actuator/health
```

`{"status":"UP"}` 이 나오면 정상입니다.

## 8. DB 마이그레이션 체크리스트 (2026-04)

최근 추가된 마이그레이션. Flyway가 서버 시작 시 자동 실행한다.

| 마이그레이션 | 내용 |
|-------------|------|
| V84 | 경쟁사 테이블 + batch_summary_competitors junction + RSS feeds |
| V85 | batch_summary_competitors 복합 인덱스 |
| V87 | `__competitor__` 시스템 카테고리 자동 생성 |
| V88 | user_accounts.slack_member_id 컬럼 추가 |
| V89 | competitors.aliases + exclude_keywords 컬럼 추가 |

> `__competitor__` 카테고리는 V87에서 자동 생성되므로 수동 생성 불필요.

## 9. 환경변수 변경사항

- `SLACK_CHANNEL_ID` 환경변수는 더 이상 사용되지 않는다. 런타임 설정의 `slackDefaultChannelId`가 제거되고 `opsLogChannelId`로 대체되었다.
- `slack_member_id` 컬럼 추가로 Slack DM 발송 시 사용자 식별이 개선되었다.

## 접속 URL

| 구분 | URL |
|------|-----|
| 로그인 | http://localhost:8086/login |
| 관리자 | http://localhost:8086/admin |
| 유저 | http://localhost:8086/user |
