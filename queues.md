# Очереди и отложенное выполнение
## Предварительные требования
- Redis для кеширования
- Redis-commander для просмотра (опционально)
- Kafka
- Kafka-UI для просмотра (опционально)
## Запуск приложения
```shell
docker-compose up -d
```
## Подготовка данных
### Создание пользователей
```shell
for i in $(seq 11 20); do
    JSON='{"first_name": "user'$i'","second_name": "second'$i'", "birthdate": "1991-11-01", "biography": "biography", "city": "city", "password": "pass"}'
    USERID=$(curl -s -X POST  --url http://127.0.0.1:8081/api/user/register  --header 'content-type: application/json'  --data "$JSON"  | jq -r '.user_id')
    echo $USERID >> ./users.lst
done
```
## Тестирование
### Общий алгоритм
1. Пользователь создает пост
2. Увеличиваем счетчик в Redis: celebrity:{userId}
3. Если счетчик > 10 за 5 минут:
    - Помечаем как "селебрити"
    - Создаем батчи для обновления лент друзей
    - Планируем полную материализацию лент друзей
4. Если не селебрити:
    - Обычное обновление лент друзей
### Проверка плучения поста через WebSocket
- добавляем одного друга
```shell
#!/bin/bash
LOGIN_JSON='{"id":"f7cc94c1-cd15-4f53-8d5a-390c2cbbd379","password":"pass"}'
TOKEN=$(curl -s -X POST  --url http://127.0.0.1:8081/api/login  --header 'content-type: application/json'  --data "$LOGIN_JSON"  | jq -r '.token')
AUTH='authorization: Bearer '$TOKEN
curl -X PUT http:/localhost:8081/api/friend/set/${USERID} -H "$AUTH"
```
- логинимся как друг и получаем токен
```shell
#!/bin/bash
LOGIN_JSON='{"id":"f7cc94c1-cd15-4f53-8d5a-390c2cbbd379","password":"pass"}'
TOKEN=$(curl -s -X POST  --url http://127.0.0.1:8081/api/login  --header 'content-type: application/json'  --data "$LOGIN_JSON"  | jq -r '.token')
echo $TOKEN
```
- открываем в браузере страницу и вводим полученный токен
```html
<html>
<head><title>WS Test</title></head>
<body>
    <h1>WebSocket Test</h1>
    <div id="status">Disconnected</div>
    <div id="messages"></div>
    
    <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js"></script>
    
    <script>
        // Получить токен 
        const token = prompt("Enter JWT token:");
        
        // SockJS с токеном в URL
        const socket = new SockJS(`http://localhost:8081/ws?token=${token}`);
        const stompClient = Stomp.over(socket);
        
        stompClient.connect(
            { Authorization: `Bearer ${token}` },  // Заголовки
            function(frame) {
                document.getElementById('status').innerHTML = 'Connected: ' + frame;
                
                stompClient.subscribe('/user/post/feed/posted', function(message) {
                    const post = JSON.parse(message.body);
                    document.getElementById('messages').innerHTML += 
                        `<p>New post: ${post.postId} - ${post.postText}</p>`;
                });
            },
            function(error) {
                document.getElementById('status').innerHTML = 'Error: ' + error;
            }
        );
    </script>
</body>
</html>
```
- создаем пост
```shell
#!/bin/bash
LOGIN_JSON='{"id":"'$1'","password":"pass"}'
TOKEN=$(curl -s -X POST  --url http://127.0.0.1:8081/api/login  --header 'content-type: application/json'  --data "$LOGIN_JSON"  | jq -r '.token')
AUTH='authorization: Bearer '$TOKEN
i=$(date)
POST='{ "text": "Test Post on '$i' of user_id='$1'"}'
curl --request POST  --url http://127.0.0.1:8081/api/post/create --header "$AUTH" --header 'content-type: application/json' --data "$POST"	
```
- в браузере проверяем получение
### Проверка материализации ленты
- добавляем еще друзей пользователю
```shell
#!/bin/bash
LOGIN_JSON='{"id":"f7cc94c1-cd15-4f53-8d5a-390c2cbbd379","password":"pass"}'
TOKEN=$(curl -s -X POST  --url http://127.0.0.1:8081/api/login  --header 'content-type: application/json'  --data "$LOGIN_JSON"  | jq -r '.token')

AUTH='authorization: Bearer '$TOKEN
for i in $(seq 11 20); do
    JSON='{"first_name": "user'$i'","second_name": "second'$i'", "birthdate": "1991-11-01", "biography": "biography", "city": "city", "password": "pass"}'
    USERID=$(curl -s -X POST  --url http://127.0.0.1:8081/api/user/register  --header 'content-type: application/json'  --data "$JSON"  | jq -r '.user_id')
	curl -X PUT http:/localhost:8081/api/friend/set/${USERID} -H "$AUTH"
    echo $USERID >> ./users.lst
done
```
- создаем посты
```shell
#!/bin/sh

login() {
	local LOGIN_JSON='{"id":"'$1'","password":"pass"}'
	local TOKEN=$(curl -s -X POST  --url http://127.0.0.1:8081/api/login  --header 'content-type: application/json'  --data "$LOGIN_JSON"  | jq -r '.token')
	#echo $TOKEN

	local AUTH='authorization: Bearer '$TOKEN
	echo ${AUTH}
}

AUTH=$(login $1)
for i in $(seq 1 150 ); do
	POST='{ "text": "Post N'$i' of user_id='$1'"}'
	POST_ID=$(curl -s --request POST  --url http://127.0.0.1:8081/api/post/create --header "${AUTH}" --header 'content-type: application/json' --data "$POST" | jq -r '.id')
done
```
- смотрим логи приложения и топик кафки