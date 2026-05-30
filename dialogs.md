# Выделение модуля диалогов в отдельный сервис
## Предварительные требования
- Postgresql
- Redis
- Redis-commander
- Модуль диалогов socail-net-dialogs
## Запуск
```shell
docker-compose up -d
```
## Подготовка данных - создание пользователей
```shell
#!/bin/sh

rm -f ./users.lst

for i in $(seq 1 10); do
    JSON='{"first_name": "user'$i'","second_name": "second'$i'", "birthdate": "1991-11-01", "biography": "biography", "city": "city", "password": "pass"}'
    USERID=$(curl -s -X POST  --url http://127.0.0.1:8081/api/user/register  --header 'content-type: application/json'  --data "$JSON"  | jq -r '.user_id')
    echo $USERID >> ./users.lst
done

```
### Тестирование
### Вызов монолита
```shell
#!/bin/sh

counter=0

for usr in $(cat ./users.lst); do
    LOGIN_JSON='{"id":"'$usr'","password":"pass"}'
    TOKEN=$(curl -s -X POST  --url http://127.0.0.1:8081/api/login  --header 'content-type: application/json'  --data "$LOGIN_JSON"  | jq -r '.token')

       for to_usr in $(cat ./users.lst); do
               if [[ "$usr" != "$to_usr" ]] ; then
                       for i in $(seq 1 3); do
                               POST='{ "text": "Message N'$i' of user_id='$usr' to user_id='$to_usr' on url=localhost:8081 - socail-net"}'
                               response=$(curl -s -X POST  --url http://127.0.0.1:8081/dialog/$to_usr/send --header "authorization: Bearer $TOKEN" --header 'content-type: application/json' --data "$POST")
                               echo "$POST $response" >> messages.lst
                       done
               fi
       done
```
### Вызов сервиса диалогов
```shell
#!/bin/sh

counter=0

for usr in $(cat ./users.lst); do
    LOGIN_JSON='{"id":"'$usr'","password":"pass"}'
    TOKEN=$(curl -s -X POST  --url http://127.0.0.1:8081/api/login  --header 'content-type: application/json'  --data "$LOGIN_JSON"  | jq -r '.token')

        for to_usr in $(cat ./users.lst); do
                if [[ "$usr" != "$to_usr" ]] ; then
                        for i in $(seq 1 3); do
                                POST='{ "text": "Message N'$i' of user_id='$usr' to user_id='$to_usr' on url=localhost:8083 - socal-net-dialogs"}'
                                response=$(curl -s -X POST  --url http://127.0.0.1:8083/internal/dialog/$to_usr/send --header "authorization: Bearer $TOKEN" --header "X-User-Id: $usr" --header 'content-type: application/json' --data "$POST")
                                echo "$POST $response" >> messages.lst
                        done
                fi
        done
        ((counter++))
        echo $counter
done 

```