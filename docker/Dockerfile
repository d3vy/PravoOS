FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY pravoos-common/pom.xml pravoos-common/pom.xml
COPY pravoos-common-web/pom.xml pravoos-common-web/pom.xml
COPY pravoos-observability/pom.xml pravoos-observability/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY llm-service/pom.xml llm-service/pom.xml
COPY ai-service/pom.xml ai-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
RUN mvn -B -ntp -q dependency:go-offline || true
COPY pravoos-common/src pravoos-common/src
COPY pravoos-common-web/src pravoos-common-web/src
COPY pravoos-observability/src pravoos-observability/src
COPY api-gateway/src api-gateway/src
COPY user-service/src user-service/src
COPY llm-service/src llm-service/src
COPY ai-service/src ai-service/src
COPY notification-service/src notification-service/src
RUN mvn -B -ntp -q clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS user-service
RUN apk add --no-cache wget
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY --from=build /build/user-service/target/user-service-*.jar app.jar
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8081/actuator/health/liveness | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:21-jre-alpine AS llm-service
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

FROM eclipse-temurin:21-jre-alpine AS ai-service
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

FROM eclipse-temurin:21-jre-alpine AS api-gateway
RUN apk add --no-cache wget
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY --from=build /build/api-gateway/target/api-gateway-*.jar app.jar
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8090/actuator/health/liveness | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:21-jre-alpine AS notification-service
RUN apk add --no-cache wget
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY --from=build /build/notification-service/target/notification-service-*.jar app.jar
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8083/actuator/health/liveness | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
