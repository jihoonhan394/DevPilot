# syntax=docker/dockerfile:1
#
# DevPilot API runtime image — linux/amd64 + linux/arm64 둘 다 지원한다.
#   amd64 = 현재 자체 서버(Rocky Linux 9 x86_64), arm64 = 공개 전환 후보인 OCI Always Free Ampere A1
#   release.yml이 ubuntu-24.04 runner에서 buildx + QEMU로 두 아키텍처를 함께 빌드한다 (docs/10 §1.4, §7.2)
#   이 Dockerfile 자체에는 아키텍처 의존이 없다 (base 이미지가 멀티아치, jar는 바이트코드)
# 기준: docs/10-deployment-and-operations.md §5.3 (2026-09-18)
#
# 빌드 컨텍스트는 backend/ 이다. jar는 이미지 밖에서 먼저 만든다:
#   ./gradlew bootJar            -> build/libs/devpilot-api.jar (build.gradle.kts의 archiveFileName)
#   docker build -t devpilot-api:dev .
#
# JVM 메모리 옵션은 이미지에 넣지 않고 compose.prod.yml의 JAVA_TOOL_OPTIONS(-Xmx1g)로 준다.

# TODO(S2 P1): pin @sha256 digest (docs/07 §11.5, docs/10 §7.4) — 예: eclipse-temurin:25-jre@sha256:<digest>, Dependabot docker가 갱신
FROM eclipse-temurin:25-jre

RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --no-create-home --shell /usr/sbin/nologin app \
    && mkdir -p /app \
    && chown root:root /app

WORKDIR /app

# jar는 root 소유 + 읽기 전용: 실행 사용자(app)가 바꿀 수 없다
COPY --chown=root:root --chmod=0444 build/libs/devpilot-api.jar /app/app.jar

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
