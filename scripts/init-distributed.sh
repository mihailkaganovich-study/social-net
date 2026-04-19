#!/bin/bash
# scripts/init-distributed.sh

set -e

echo "=== Starting distributed table initialization ==="
echo "Waiting for workers to be registered by manager..."

# Ждем регистрации worker'ов
MAX_WAIT=120
WAIT_INTERVAL=2
ELAPSED=0

while [ $ELAPSED -lt $MAX_WAIT ]; do
    WORKER_COUNT=$(PGPASSWORD=postgres psql -h otus_citus_master -U postgres -d postgres -t -c \
        "SELECT COUNT(*) FROM pg_dist_node WHERE noderole = 'primary';" 2>/dev/null | xargs || echo "0")

    echo "Registered workers: $WORKER_COUNT (waited ${ELAPSED}s)"

    if [ "$WORKER_COUNT" -ge 1 ]; then
        echo "✓ Workers registered successfully!"
        break
    fi

    sleep $WAIT_INTERVAL
    ELAPSED=$((ELAPSED + WAIT_INTERVAL))
done

if [ "$WORKER_COUNT" -eq 0 ]; then
    echo "❌ No workers registered after ${MAX_WAIT}s. Exiting."
    exit 1
fi

echo "=== Creating distributed tables ==="

# Выполняем SQL напрямую
PGPASSWORD=postgres psql -h otus_citus_master -U postgres -d postgres <<-'EOF'
\set ON_ERROR_STOP on

-- Проверяем наличие worker'ов
DO $$
DECLARE
    worker_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO worker_count FROM pg_dist_node WHERE noderole = 'primary';

    IF worker_count = 0 THEN
        RAISE EXCEPTION 'No workers registered. Cannot create distributed tables.';
    END IF;

    RAISE NOTICE 'Found % registered workers', worker_count;
END $$;
-- Создание расширения Citus
CREATE EXTENSION IF NOT EXISTS citus;
-- Включение необходимых расширений
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

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

-- Создаем индексы перед распределением
CREATE INDEX IF NOT EXISTS idx_dialog_messages_created_at
    ON dialog_messages(dialog_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dialog_messages_from_user
    ON dialog_messages(from_user_id);
CREATE INDEX IF NOT EXISTS idx_dialog_messages_to_user
    ON dialog_messages(to_user_id);

-- Распределяем таблицы
SELECT create_distributed_table('dialog_messages', 'dialog_id');
SELECT create_distributed_table('dialogs', 'dialog_id');
SELECT create_distributed_table('unread_messages_count', 'dialog_id');
SELECT create_distributed_table('users', 'id');

-- Создаем шардированные индексы после распределения
CREATE INDEX IF NOT EXISTS idx_dialogs_user1 ON dialogs(user1_id);
CREATE INDEX IF NOT EXISTS idx_dialogs_user2 ON dialogs(user2_id);
CREATE INDEX IF NOT EXISTS idx_dialogs_user_pair ON dialogs(user1_id, user2_id);

-- Представления для мониторинга
CREATE OR REPLACE VIEW shard_distribution AS
SELECT
    s.table_name::text,
    s.nodename,
    s.nodeport,
    COUNT(*) as shard_count,
    pg_size_pretty(SUM(s.shard_size)) as total_size
FROM citus_shards s
GROUP BY s.table_name, s.nodename, s.nodeport;

CREATE OR REPLACE VIEW cluster_nodes AS
SELECT
    nodeid,
    nodename,
    nodeport,
    noderole,
    nodecluster,
    isactive,
    shouldhaveshards
FROM pg_dist_node
ORDER BY noderole, nodename;

EOF

echo "=== Distributed initialization completed ==="