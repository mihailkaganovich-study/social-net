# Репликация: практическое применение
## Общие данные
1. В файле master-init/01-init-db.sql - создание таблицы и первичное заполнение

## Подготовка
1. Создаем сеть, запоминаем адрес
    ```shell
    docker network create study_network
    docker network inspect study_network | grep Subnet # Запомнить маску сети
    ```
2. В файле pg_hba.conf прописываем доступы
3. Запускаем контейнер pg-master
   ```bash
   docker-compose -f docker-compose-replica.yaml up pg-master -d
   ```
4. Запускаем приложение
   ```shell
   docker-compose -f docker-compose-replica.yaml up app -d
   ```
5. Выполняем нагрузку - файл homework3/homework3.jmx 
   1. 1000 пользователей
   2. в течении 10 секунд
   3. запросы /api/user/get и /api/user/search

## Перевод мастера в режим репликации
1. Меняем postgresql.conf на мастере
   ```conf
   ssl = off
   wal_level = replica
   synchronous_commit = off
   max_wal_senders = 4
   hba_file = '/var/lib/postgresql/data/pg_hba.conf' 
   ```
2. Подключаемся к мастеру и для пользователя study_user даем роль для репликации
   ```shell
   docker exec -it pgmaster su - postgres
   psql -U study_user -d study_db
   ALTER USER study_user WITH REPLICATION;
   GRANT ALL PRIVILEGES ON SCHEMA public TO study_user;
   GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO study_user;
   ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO study_user;    
   exit
   exit
    ```

3. Добавляем записи в `/var/lib/postgresql/data/pg_hba.conf` с `subnet` с первого шага
    ```
   # Replication connections from slaves (любые IP в сети Docker)
   host    replication     study_user      172.18.0.0/16           scram-sha-256
   
   # Application connections to study_db
   host    study_db        study_user      0.0.0.0/0               scram-sha-256
   
   # Allow slaves to connect to master for replication (дублируем для надежности)
   host    all             study_user      172.18.0.0/16           scram-sha-256
    ```

4. Перезапускаем мастер
    ```shell
    docker restart pgmaster
    ```
## Запуск репликации
1. Запускаем слэйвы
```shell
docker-compose -f docker-compose-replica.yaml up pgslave1 -d
docker-compose -f docker-compose-replica.yaml up pgslave2 -d
```
2. Проверяем стаус репликации
```shell
   docker exec -it pgmaster psql -U study_user -d study_db -c 'select application_name, client_addr,sync_state from pg_stat_replication;'
```
## Проверка репликации
1. Добавляем запись
```shell
   $ curl --request POST \
      --url http://127.0.0.1:8081/api/user/register \
      --header 'content-type: application/json' \
      --data '{
      "first_name": "Александр",
      "second_name": "Александров",
      "birthdate": "1991-11-01",
      "biography": "Дизайнер",
      "city": "Мурманск",
      "password": "987654321"
     }'
 ```
2. Проверем запись на слэйвах
```shell
   $ docker exec -it pgslave1 psql -U study_user -d study_db -c "select * from users where id='62886c9c-2a65-47ea-bd92-df29373bd709';"
   $ docker exec -it pgslave2 psql -U study_user -d study_db -c "select * from users where id='62886c9c-2a65-47ea-bd92-df29373bd709';"
```
## Нагрузочное чтение
1. Выполняем нагрузку - файл homework3/homework3.jmx
   1. 1000 пользователей
   2. в течении 10 секунд
   3. запросы /api/user/get и /api/user/search

## Настройка кворумной репликации
1. Включаем синхронную репликацию на мастере
   - меняем файл `postgresql.conf`
       ```conf
       synchronous_commit = on
       synchronous_standby_names = 'FIRST 1 (pgslave1, pgslave2)'
       ```

   - перечитываем конфиг
       ```shell
       docker exec -it pgmaster psql -U study_user -d study_db
       select pg_reload_conf();
       exit;
       ```

2. Убеждаемся, что реплика стала синхронной
    ```shell
    docker exec -it pgmaster psql -U study_user -d study_db
    exit;
    ```

3. Создадим тестовую таблицу на мастере и проверим репликацию
    ```shell
    docker exec -it pgmaster psql -U study_user -d study_db
    create table test(id bigint);
    insert into test(id) values(1);
    select * from test;
    exit;
    ```

4. Проверим наличие данных на слэйвах
    ```shell
    docker exec -it pgslave1 psql -U study_user -d study_db -c "select * from test;"
    docker exec -it pgslave2 psql -U study_user -d study_db -c "select * from test;"
    ```
## Нагрузочная запись и переключение
1. Запускам нагрузку - RecordRequest.jmx
2. Останавливаем мастер
   ```shell
   docker stop pgmaster
   ```
3. Проверяем количество записей на слэйвах
    ```shell
    docker exec -it pgslave1 psql -U study_user -d study_db -c "select count(1) from test;"
    docker exec -it pgslave2 psql -U study_user -d study_db -c "select count(1) from test;"
    ```
4. Промоутим pgslave1
   ```shell
   docker exec -it pgslave1 psql -U study_user -d study_db
   select pg_promote();
   ```
5. Меняем конфигурацию
   - меняем файл `postgresql.conf`
       ```conf
       synchronous_commit = on
       synchronous_standby_names = 'ANY 1 (pgmaster, pgslave2)'
       ```

   - перечитываем конфиг
       ```shell
       docker exec -it pgslave1 psql -U study_user -d study_db
       select pg_reload_conf();
       exit;
       ```
   - переключаем pgslave2
      ```shell
      docker exec -it pgslave2 psql -U study_user -d study_db
      ALTER SYSTEM SET primary_conninfo = 'host=pg-slave1 port=5432 user=study_user password=study_password application_name=pgslave2';
      SELECT pg_reload_conf();
      ```
   - проверяем статус
     ```shell
     docker exec -it pgslave1 psql -U study_user -d study_db
     select application_name, client_addr,sync_state from pg_stat_replication;
     insert into test values(1000000);
     ```
   - проверяем репликацию
     ```shell
     docker exec -it pgslave2 psql -U study_user -d study_db -c "select * from test where id=1000000"
     ```