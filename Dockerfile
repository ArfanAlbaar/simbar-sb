# Tahap 1: Build dengan Maven dan Java 21 dari Eclipse Temurin
FROM eclipse-temurin:21 AS builder

WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw clean package -DskipTests

# Tahap 2: Jalankan menggunakan image JDK ringan
FROM eclipse-temurin:21-jdk

WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 80
ENTRYPOINT ["java", "-Dserver.port=80", "-jar", "app.jar"]
