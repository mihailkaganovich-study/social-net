# Процесс решардинга Citus без даунтайма
## Запуск сервисов
```shell
docker-compose up master -d
docker-compose up manager -d
docker-compose up worker --scale worker=2 -d
docker-compose up init-distributed -d
docker-compose up app -d
```
## Генерация тестовых данных
### Регистрация пользователей
```shell
#!/bin/sh

for i in $(seq 1 100); do
    JSON='{"first_name": "user'$i'","second_name": "second'$i'", "birthdate": "1991-11-01", "biography": "biography", "city": "city", "password": "pass"}'
    echo $JSON
    USERID=$(curl -s -X POST  --url http://127.0.0.1:8081/api/user/register  --header 'content-type: application/json'  --data "$JSON"  | jq -r '.user_id')
    echo $USERID >> ./users.lst
done
```
### Создаине диалогов
```shell
#!/bin/sh

counter=0

for usr in $(cat ./users.lst); do
    LOGIN_JSON='{"id":"'$usr'","password":"pass"}'
    TOKEN=$(curl -s -X POST  --url http://127.0.0.1:8081/api/login  --header 'content-type: application/json'  --data "$LOGIN_JSON"  | jq -r '.token')
	
	for to_usr in $(cat ./users.lst); do
		if [[ "$usr" != "$to_usr" ]] ; then
			for i in $(seq 1 10); do
				POST='{ "text": "Message N'$i' of user_id='$usr' to user_id='$to_usr'"}'
				response=$(curl -s -X POST  --url http://127.0.0.1:8081/dialog/$to_usr/send --header "authorization: Bearer $TOKEN" --header 'content-type: application/json' --data "$POST")
				echo "$POST $response" >> messages.lst
			done
		fi
	done
	((counter++))
	echo $counter
done 
```
## Подготовка к решардингу

### 1. Проверка текущего состояния кластера
```sql
-- Проверка текущих worker нод
SELECT * FROM citus_get_active_worker_nodes();
```
### 2. Добавление worker
```shell
docker-compose stop
docker-compose up master -d
docker-compose up manager -d
docker-compose up worker --scale worker=4 -d
docker-compose up app -d
```
### 3. Установка логической репликации
```shell
docker exec -it otus_cites_master psql -U postgres
```
- меняем на мастере и на воркерах
```sql
alter system set wal_level=logical;
select run_command_on_workers('alter system set wal_level=logical');
```
- перегружаем для применения конфигурации
```shell
docker-compose stop
docker-compose up master -d
docker-compose up manager -d
docker-compose up worker --scale worker=4 -d
docker-compose up app -d
```
- запускаем решардинг
```sql
select citus_rebalance_start();
select citus_rebalance_status();
```
- проверяем
```sql
select table_name,nodename, sum(shard_size) from citus_shards group by table_name,nodename;
```