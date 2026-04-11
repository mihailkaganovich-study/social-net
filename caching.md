# Кеширование ленты
## Предварительные требования
- Redis для кеширования
- Redis-commander для просмотра (опционально)
## Запуск приложения
```shell
docker-compose up -d
```
## Тестирование
1. Создать пользователей
```shell
curl --request POST \
  --url http://127.0.0.1:8081/api/user/register \
  --header 'content-type: application/json' \
  --data '{
  "first_name": "Михаил",
  "second_name": "Михайлов",
  "birthdate": "1991-11-01",
  "biography": "Строитель",
  "city": "Белгород",
  "password": "987654321"
}'
```
2. Добавить друзей
```shell
curl --request PUT \
  --url http://127.0.0.1:8081/api/friend/set/d0d81b96-6bdc-4607-b505-b6568c7c6b91 \
  --header 'authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkMGQ4MWI5Ni02YmRjLTQ2MDctYjUwNS1iNjU2OGM3YzZiOTEiLCJpYXQiOjE3NzU4MzM0NjIsImV4cCI6MTc3NTkxOTg2Mn0.sdkCgt994shu2O4RQx9cE4KpIeTe5Sid56BT4uoZjvI'
```
3. Создать посты скриптом - в параметре user_id 
```shell
#!/bin/sh

LOGIN_JSON='{"id":"'$1'","password":"987654321"}'
TOKEN=$(curl -s -X POST  --url http://127.0.0.1:8081/api/login  --header 'content-type: application/json'  --data "$LOGIN_JSON"  | jq -r '.token')

AUTH='authorization: Bearer '$TOKEN

for i in $(seq 4 350); do
    POST='{ "text": "Post N'$i' of user_id='$1'"}'
    POST_ID=$(curl -s --request POST  --url http://127.0.0.1:8081/api/post/create --header "authorization: Bearer $TOKEN" --header 'content-type: application/json' --data "$POST" | jq -r '.id')
    echo $POST_ID>>./$1.log
done
```
4. Проверка ленты
- скрипт - параметр user_id
```shell
#!/bin/sh

LOGIN_JSON='{"id":"'$1'","password":"987654321"}'
TOKEN=$(curl -s -X POST  --url http://127.0.0.1:8081/api/login  --header 'content-type: application/json'  --data "$LOGIN_JSON"  | jq -r '.token')

AUTH='authorization: Bearer '$TOKEN
curl -s --request GET  --url "http://127.0.0.1:8081/api/post/feed?offset=950&limit=100" --header "authorization: Bearer $TOKEN" --header 'content-type: application/json'
```
- запуск - меняя offset и limit - проверить вывод количества постов
```shell
./get_feed.sh da2c9502-1446-4eb4-a775-d17be27b5e46 | jq length
```