# Gunakan base image Java
FROM openjdk:21-jdk-alpine

# Set working directory di dalam container
WORKDIR /app

# Salin file jar hasil build
COPY target/*.jar app.jar

# Jalankan aplikasi di port 80
EXPOSE 80

# Jalankan Spring Boot (ubah server.port ke 80)
ENTRYPOINT ["java", "-Dserver.port=80", "-jar", "app.jar"]
