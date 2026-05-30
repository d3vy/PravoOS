FROM eclipse-temurin:21-jre-alpine AS user-service
RUN apk add --no-cache wget
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY docker/jars/user-service.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:21-jre-alpine AS ai-service
RUN apk add --no-cache wget
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring \
    && mkdir -p /app/documents /app/logs/ai-service \
    && chown -R spring:spring /app
USER spring
COPY --chown=spring:spring docker/jars/ai-service.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:21-jre-alpine AS api-gateway
RUN apk add --no-cache wget
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY docker/jars/api-gateway.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:21-jre-alpine AS notification-service
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY docker/jars/notification-service.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
