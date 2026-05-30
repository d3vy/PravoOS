# syntax=docker/dockerfile:1

FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY docker/maven/settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY api-gateway/pom.xml api-gateway/
COPY user-service/pom.xml user-service/
COPY ai-service/pom.xml ai-service/
COPY notification-service/pom.xml notification-service/
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -q
COPY api-gateway/src api-gateway/src
COPY user-service/src user-service/src
COPY ai-service/src ai-service/src
COPY notification-service/src notification-service/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine AS user-service
RUN apk add --no-cache wget
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY --from=builder /app/user-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:21-jre-alpine AS ai-service
RUN apk add --no-cache wget
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY --from=builder /app/ai-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:21-jre-alpine AS api-gateway
RUN apk add --no-cache wget
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY --from=builder /app/api-gateway/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:21-jre-alpine AS notification-service
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY --from=builder /app/notification-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
