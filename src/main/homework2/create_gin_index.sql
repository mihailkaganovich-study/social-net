-- Включение расширения
CREATE EXTENSION pg_trgm;
CREATE INDEX idx_gin_users 
ON users USING GIN (first_name gin_trgm_ops, second_name gin_trgm_ops);

--такой индекс был выбран исходя из запроса где поиск идет по вхождению строки внутри
-- т.е. LIKE '%abc%'
--B-tree в такого рода запросах не работает