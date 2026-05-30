FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY docker/maven/settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY api-gateway/pom.xml api-gateway/
COPY user-service/pom.xml user-service/
COPY ai-service/pom.xml ai-service/
COPY notification-service/pom.xml notification-service/
RUN mvn dependency:go-offline -pl ai-service -am -q
COPY ai-service/src ai-service/src
RUN mvn clean package -pl ai-service -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY --from=build /app/ai-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
