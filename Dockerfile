
FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

COPY build/libs/v0.5-PRE-ALPHA.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"]
