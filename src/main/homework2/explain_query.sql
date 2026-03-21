EXPLAIN analyze 
SELECT id, first_name, second_name, birthdate, biography, city 
FROM users WHERE first_name LIKE '%аба%' AND second_name LIKE '%ров%'
ORDER BY id;