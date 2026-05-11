# ============================================================
# Clipping MCP Server — 멀티 스테이지 Docker 빌드
# ============================================================
# 스테이지 1: 프론트엔드 빌드 (Node 22 + pnpm)
# 스테이지 2: 백엔드 빌드 (Gradle + JDK 21)
# 스테이지 3: 런타임 (JRE 21 slim)
# ============================================================

# ----------------------------------------------------------
# 스테이지 1: 프론트엔드 빌드
# ----------------------------------------------------------
FROM node:22.16.0-slim AS frontend-build

# pnpm 활성화
RUN corepack enable && corepack prepare pnpm@latest --activate

WORKDIR /app/frontend

# 의존성 레이어 캐싱: lockfile 먼저 복사
COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

# 소스 복사 후 빌드 (출력: /app/src/main/resources/static)
COPY frontend/ ./
# vite.config.ts의 outDir이 상대경로(../src/main/resources/static)이므로 상위 디렉터리 생성
RUN mkdir -p /app/src/main/resources/static
RUN pnpm run build


# ----------------------------------------------------------
# 스테이지 2: 백엔드 빌드
# ----------------------------------------------------------
FROM eclipse-temurin:21.0.7_6-jdk AS backend-build

WORKDIR /app

# Gradle 래퍼 + 빌드 설정 먼저 복사 (의존성 캐싱)
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
RUN chmod +x gradlew

# Gradle 의존성 미리 다운로드 (캐시 레이어)
RUN ./gradlew dependencies --no-daemon || true

# 프론트엔드 빌드 결과물 복사
COPY --from=frontend-build /app/src/main/resources/static/ src/main/resources/static/

# 백엔드 소스 복사
COPY src/ src/

# 프론트엔드 빌드 스킵 — 이미 스테이지 1에서 완료
RUN ./gradlew bootJar --no-daemon -PskipFrontendBuild=true


# ----------------------------------------------------------
# 스테이지 3: 프로덕션 런타임
# ----------------------------------------------------------
FROM eclipse-temurin:21.0.7_6-jre-alpine AS runtime

# 보안: 비루트 사용자로 실행
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# curl 설치 (헬스체크용)
RUN apk add --no-cache curl

WORKDIR /app

# JAR 복사
COPY --from=backend-build /app/build/libs/*.jar app.jar

# 파일 소유권 변경
RUN chown -R appuser:appgroup /app

USER appuser

# 서버 포트 노출
EXPOSE 8086

# 헬스체크: Spring Actuator 엔드포인트 사용
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -sf http://localhost:8086/actuator/health || exit 1

# JVM 최적화 옵션과 함께 실행
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
