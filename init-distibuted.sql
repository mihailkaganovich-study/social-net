-- init-distributed.sql
-- Выполняется после регистрации worker'ов

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

-- Создаем индексы
CREATE INDEX IF NOT EXISTS idx_dialog_messages_created_at
    ON dialog_messages(dialog_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dialog_messages_from_user
    ON dialog_messages(from_user_id);
CREATE INDEX IF NOT EXISTS idx_dialog_messages_to_user
    ON dialog_messages(to_user_id);
CREATE INDEX IF NOT EXISTS idx_dialogs_user1 ON dialogs(user1_id);
CREATE INDEX IF NOT EXISTS idx_dialogs_user2 ON dialogs(user2_id);

-- Распределяем таблицы
SELECT create_distributed_table('dialog_messages', 'dialog_id');
SELECT create_distributed_table('dialogs', 'dialog_id');
SELECT create_distributed_table('unread_messages_count', 'dialog_id');

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

-- Обновляем статус
INSERT INTO init_status (stage, details)
SELECT 'distributed_init', jsonb_build_object(
        'status', 'completed',
        'workers', COUNT(*),
        'tables_distributed', 3
                           )
FROM pg_dist_node
WHERE noderole = 'primary';

DO $$
DECLARE
worker_count INTEGER;
    shard_count INTEGER;
BEGIN
SELECT COUNT(*) INTO worker_count FROM pg_dist_node WHERE noderole = 'primary';
SELECT COUNT(*) INTO shard_count FROM citus_shards;

RAISE NOTICE '========================================';
    RAISE NOTICE 'Distributed initialization completed!';
    RAISE NOTICE 'Active workers: %', worker_count;
    RAISE NOTICE 'Total shards created: %', shard_count;
    RAISE NOTICE '========================================';
END $$;