# Dockerfile
#FROM openjdk:17-jdk-slim AS build
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
#FROM openjdk:17-jdk-slim
FROM openjdk:17.0.1-jdk-slim

WORKDIR /app

# Копирование собранного jar файла
COPY --from=build /app/target/*.jar app.jar

# Создание пользователя для безопасности
RUN useradd -m -u 1000 appuser && chown -R appuser:appuser /app
USER appuser

# Порт приложения
EXPOSE 8081

# Запуск приложения
ENTRYPOINT ["java", "-jar", "app.jar"]