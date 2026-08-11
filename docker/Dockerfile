# Base images are pinned by digest, not tag: a tag can be repointed at a
# different image after review. Dependabot (docker ecosystem) bumps the digests.
FROM maven:3-eclipse-temurin-26@sha256:6206ae5e460fbc803743b53addc31c5caca04582cf6a99f0f91df29c54954b52 AS build
WORKDIR /build
COPY pom.xml .
COPY pravoos-common/pom.xml pravoos-common/pom.xml
COPY pravoos-common-web/pom.xml pravoos-common-web/pom.xml
COPY pravoos-common-test/pom.xml pravoos-common-test/pom.xml
COPY pravoos-observability/pom.xml pravoos-observability/pom.xml
COPY pravoos-cloud/pom.xml pravoos-cloud/pom.xml
COPY discovery-server/pom.xml discovery-server/pom.xml
COPY config-server/pom.xml config-server/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY llm-service/pom.xml llm-service/pom.xml
COPY ai-service/pom.xml ai-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY coverage-report/pom.xml coverage-report/pom.xml
RUN mvn -B -ntp -q dependency:go-offline || true
COPY pravoos-common/src pravoos-common/src
COPY pravoos-common-web/src pravoos-common-web/src
COPY pravoos-common-test/src pravoos-common-test/src
COPY pravoos-observability/src pravoos-observability/src
COPY pravoos-cloud/src pravoos-cloud/src
COPY discovery-server/src discovery-server/src
COPY config-server/src config-server/src
COPY api-gateway/src api-gateway/src
COPY user-service/src user-service/src
COPY llm-service/src llm-service/src
COPY ai-service/src ai-service/src
COPY notification-service/src notification-service/src
RUN mvn -B -ntp -q clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c AS jre-base

FROM jre-base AS user-service
RUN apk add --no-cache wget
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY --from=build /build/user-service/target/user-service-*.jar app.jar
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8081/actuator/health/liveness | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM jre-base AS llm-service
RUN apk add --no-cache wget
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring \
    && mkdir -p /app/logs/llm-service \
    && chown -R spring:spring /app
USER spring
COPY --from=build --chown=spring:spring /build/llm-service/target/llm-service-*.jar app.jar
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8084/actuator/health/liveness | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM jre-base AS ai-service
RUN apk add --no-cache wget
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring \
    && mkdir -p /app/documents /app/logs/ai-service \
    && chown -R spring:spring /app
USER spring
COPY --from=build --chown=spring:spring /build/ai-service/target/ai-service-*.jar app.jar
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8082/actuator/health/liveness | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM jre-base AS api-gateway
RUN apk add --no-cache wget
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY --from=build /build/api-gateway/target/api-gateway-*.jar app.jar
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8090/actuator/health/liveness | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM jre-base AS notification-service
RUN apk add --no-cache wget
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY --from=build /build/notification-service/target/notification-service-*.jar app.jar
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8083/actuator/health/liveness | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM jre-base AS discovery-server
RUN apk add --no-cache wget
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY --from=build /build/discovery-server/target/discovery-server-*.jar app.jar
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget -qO- http://localhost:8761/actuator/health/liveness | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM jre-base AS config-server
RUN apk add --no-cache wget
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY --from=build /build/config-server/target/config-server-*.jar app.jar
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget -qO- http://localhost:8888/actuator/health/liveness | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
