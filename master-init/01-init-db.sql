-- init-db.sql
-- Даем пользователю study_user права на репликацию

--ALTER USER study_user WITH REPLICATION;

--GRANT ALL PRIVILEGES ON SCHEMA public TO study_user;
--GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO study_user;
--ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO study_user;

-- Включение необходимых расширений
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Создание таблицы пользователей
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    first_name VARCHAR(100) NOT NULL,
    second_name VARCHAR(100) NOT NULL,
    birthdate DATE,
    biography TEXT,
    city VARCHAR(100),
    password_hash TEXT DEFAULT 'password',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                             );

-- Функция для обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ language 'plpgsql';

-- Триггер для updated_at
DROP TRIGGER IF EXISTS update_users_updated_at ON users;
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Добавление тестовых данных (опционально)
INSERT INTO users (first_name, second_name, birthdate, biography, city, password_hash)
SELECT
    'Иван', 'Петров', '1990-01-01', 'Разработчик, люблю программировать', 'Москва', crypt('password123', gen_salt('bf'))
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE first_name = 'Иван' AND second_name = 'Петров');

INSERT INTO users (first_name, second_name, birthdate, biography, city, password_hash)
SELECT
    'Мария', 'Иванова', '1995-05-15', 'Дизайнер, увлекаюсь фотографией', 'Санкт-Петербург', crypt('password456', gen_salt('bf'))
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE first_name = 'Мария' AND second_name = 'Иванова');



