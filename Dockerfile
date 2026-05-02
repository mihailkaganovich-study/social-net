# Dockerfile
FROM openjdk:17.0.1-jdk-slim AS build
# Установка Maven
RUN apt-get update && apt-get install -y maven

WORKDIR /app

# Копирование файлов проекта
COPY pom.xml .
COPY src ./src

# Сборка приложения
RUN mvn clean package -DskipTests

# Финальный образ
FROM openjdk:17.0.1-jdk-slim

WORKDIR /app

# Копирование собранного jar файла
COPY --from=build /app/target/*.jar app.jar

# Создание пользователя для безопасности
RUN useradd -m -u 1000 appuser && chown -R appuser:appuser /app
USER appuser

# Порт приложения
EXPOSE 8081

# `cartridge-driver` uses msgpack internals requiring sun.nio.ch access on JDK 17
ENTRYPOINT ["java", "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED", "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED", "-jar", "app.jar"]