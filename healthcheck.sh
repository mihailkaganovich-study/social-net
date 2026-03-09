#!/bin/bash
# healthcheck.sh

echo "Checking application health..."

# Проверка PostgreSQL
if docker exec study_postgres pg_isready -U study_user -d study_db > /dev/null 2>&1; then
    echo "✅ PostgreSQL is running"
else
    echo "❌ PostgreSQL is not running"
    exit 1
fi

# Проверка Spring приложения
if curl -s http://127.0.0.1:8081/actuator/health > /dev/null; then
    echo "✅ Spring application is running"
else
    echo "❌ Spring application is not running"
    exit 1
fi

# Проверка доступности API
echo "Testing API endpoints..."

# Тест регистрации
REGISTER_TEST=$(curl -s -X POST http://127.0.0.1:8081/api/user/register \
  -H "Content-Type: application/json" \
  -d '{
    "first_name": "Test",
    "second_name": "User",
    "birthdate": "1990-01-01",
    "biography": "Test user",
    "city": "Test City",
    "password": "test123"
  }')

if [[ $REGISTER_TEST == *"user_id"* ]]; then
    echo "✅ Registration endpoint works"
else
    echo "❌ Registration endpoint failed"
fi

echo "Health check completed!"