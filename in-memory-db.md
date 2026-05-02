#  In-Memory СУБД
## Предварительные требования
- Postgresql
- Tarantool
## Запуск приложения 
```shell
docker compose -f docker-compose-memory.yaml up -d
```
## Тестирование
### Подготовка данных
- Регистрация пользователей - идентификаторы сохраняем в users.lst
```shell
#!/bin/sh

for i in $(seq 1 20); do
    JSON='{"first_name": "user'$i'","second_name": "second'$i'", "birthdate": "1991-11-01", "biography": "biography", "city": "city", "password": "pass"}'
    USERID=$(curl -s -X POST  --url http://127.0.0.1:8081/api/user/register  --header 'content-type: application/json'  --data "$JSON"  | jq -r '.user_id')
    echo $USERID >> ./users.lst
done
```
- План тестирования JMeter - in-memory-db.jmx
  - Изменяем адрес для создания диалогов /dialog/{to_user}/send - отправка в PostgreSQL
  - Изменяем адрес для создания диалогов /dialog/{to_user}/list - отправка в PostgreSQL
  - Запускаем тестирование и сохраняем результат
  - Изменяем адрес для создания диалогов /memory/dialog/{to_user}/send - отправка в Tarantool
  - Изменяем адрес для создания диалогов /memory/dialog/{to_user}/list - отправка в Tarantool
  - Запускаем тестирование и сохраняем результат