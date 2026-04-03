package ru.otus.study.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.Types;

@Repository
public class TestRepository {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public int saveAndGetCount(Long id) {
        // Вставка записи
        String insertSql = "INSERT INTO test (id) VALUES (?)";
        jdbcTemplate.update(insertSql, id);

        // Подсчет общего количества записей
        String countSql = "SELECT COUNT(*) FROM test";
        return jdbcTemplate.queryForObject(countSql, Integer.class);
    }
}