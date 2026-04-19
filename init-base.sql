-- init-base.sql
-- Выполняется при старте мастера, до регистрации worker'ов

-- Создание расширения Citus
CREATE EXTENSION IF NOT EXISTS citus;
-- Включение необходимых расширений
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Базовые настройки Citus
ALTER SYSTEM SET citus.shard_count = 16;
ALTER SYSTEM SET citus.shard_replication_factor = 1;
SELECT pg_reload_conf();

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

-- Флаг для отслеживания статуса инициализации
CREATE TABLE IF NOT EXISTS init_status (
                                           id SERIAL PRIMARY KEY,
                                           stage VARCHAR(50) NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                               details JSONB
                               );

INSERT INTO init_status (stage, details)
VALUES ('base_init', '{"status": "completed"}'::jsonb)
    ON CONFLICT DO NOTHING;

DO $$
BEGIN
    RAISE NOTICE '=== Base initialization completed ===';
    RAISE NOTICE 'Tables created but not yet distributed';
    RAISE NOTICE 'Run init-distributed after workers are registered';
END $$;