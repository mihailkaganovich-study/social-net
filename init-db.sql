-- init-db.sql
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
    password_hash TEXT NOT NULL,
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

-- Создаем обычные (не распределенные) таблицы
CREATE TABLE IF NOT EXISTS dialog_messages (
                                               dialog_id UUID NOT NULL,
                                               message_id UUID NOT NULL DEFAULT gen_random_uuid(),
    from_user_id UUID NOT NULL,
    to_user_id UUID NOT NULL,
    text TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP WITH TIME ZONE,
                          PRIMARY KEY (dialog_id, message_id)
    );

CREATE TABLE IF NOT EXISTS dialogs (
                                       dialog_id UUID NOT NULL,
                                       user1_id UUID NOT NULL,
                                       user2_id UUID NOT NULL,
                                       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                       last_message_at TIMESTAMP WITH TIME ZONE,
    -- PRIMARY KEY включает dialog_id (колонку шардирования)
                                       PRIMARY KEY (dialog_id),
    -- UNIQUE constraint тоже должен включать dialog_id
    CONSTRAINT unique_user_pair UNIQUE (dialog_id, user1_id, user2_id)
    );

-- Создаем индексы для быстрого поиска
CREATE INDEX IF NOT EXISTS idx_dialogs_user1 ON dialogs(user1_id);
CREATE INDEX IF NOT EXISTS idx_dialogs_user2 ON dialogs(user2_id);
CREATE INDEX IF NOT EXISTS idx_dialogs_user_pair ON dialogs(user1_id, user2_id);

CREATE TABLE IF NOT EXISTS unread_messages_count (
                                                     dialog_id UUID NOT NULL,
                                                     user_id UUID NOT NULL,
                                                     count INTEGER DEFAULT 0,
                                                     last_updated TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                                     PRIMARY KEY (dialog_id, user_id)
    );

-- Функция для генерации dialog_id
CREATE OR REPLACE FUNCTION get_dialog_id(user1 UUID, user2 UUID)
RETURNS UUID AS $$
DECLARE
min_user UUID;
    max_user UUID;
BEGIN
    min_user := LEAST(user1, user2);
    max_user := GREATEST(user1, user2);
RETURN md5(min_user::text || '-' || max_user::text)::uuid;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Создаем индексы
CREATE INDEX IF NOT EXISTS idx_dialog_messages_created_at
    ON dialog_messages(dialog_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dialog_messages_from_user
    ON dialog_messages(from_user_id);
CREATE INDEX IF NOT EXISTS idx_dialog_messages_to_user
    ON dialog_messages(to_user_id);
CREATE INDEX IF NOT EXISTS idx_dialogs_user1 ON dialogs(user1_id);
CREATE INDEX IF NOT EXISTS idx_dialogs_user2 ON dialogs(user2_id);