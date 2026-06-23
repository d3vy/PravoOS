FROM eclipse-temurin:21-jre-alpine AS user-service
RUN apk add --no-cache wget
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY docker/jars/user-service.jar app.jar
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8081/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:21-jre-alpine AS ai-service
RUN apk add --no-cache wget
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring \
    && mkdir -p /app/documents /app/logs/ai-service \
    && chown -R spring:spring /app
USER spring
COPY --chown=spring:spring docker/jars/ai-service.jar app.jar
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8082/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:21-jre-alpine AS api-gateway
RUN apk add --no-cache wget
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY docker/jars/api-gateway.jar app.jar
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:21-jre-alpine AS notification-service
RUN apk add --no-cache wget
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY docker/jars/notification-service.jar app.jar
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8083/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
