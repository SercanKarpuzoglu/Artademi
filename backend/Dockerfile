# backend/Dockerfile
# Multi-stage: 1) Maven ile derle  2) sadece JRE + jar ile küçük çalışma imajı

# ---- 1. AŞAMA: BUILD ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Önce sadece pom.xml — bağımlılıklar cache'lensin (kod değişince tekrar indirmesin)
COPY pom.xml .
RUN mvn -q -e -B dependency:go-offline
# Sonra kaynak kod
COPY src ./src
# Testleri build sırasında ÇALIŞTIRMA (Testcontainers Docker ister; prod build'de atla)
RUN mvn -q -B clean package -DskipTests

# ---- 2. AŞAMA: RUNTIME ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# Build aşamasından sadece jar'ı al
COPY --from=build /app/target/*.jar app.jar
# Spring Boot portu (application.yml'de server.port=8081 ise eşle)
EXPOSE 8081
# Bellek sınırı container'a göre otomatik ayarlansın
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
