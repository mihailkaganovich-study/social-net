package ru.otus.study.repository;

import ru.otus.study.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<User> userRowMapper;

    @Autowired
    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRowMapper = (rs, rowNum) -> {
            User user = new User();
            user.setId(UUID.fromString(rs.getString("id")));
            user.setFirstName(rs.getString("first_name"));
            user.setSecondName(rs.getString("second_name"));
            
            LocalDate birthdate = rs.getDate("birthdate") != null 
                ? rs.getDate("birthdate").toLocalDate() 
                : null;
            user.setBirthdate(birthdate);
            
            user.setBiography(rs.getString("biography"));
            user.setCity(rs.getString("city"));
            return user;
        };
    }

    public UUID save(User user) {
        String sql = "INSERT INTO users (id, first_name, second_name, birthdate, biography, city, password_hash) " +
                    "VALUES (?, ?, ?, ?, ?, ?, crypt(?, gen_salt('bf')))";
        
        UUID userId = user.getId() != null ? user.getId() : UUID.randomUUID();
        
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setObject(1, userId, Types.OTHER);
            ps.setString(2, user.getFirstName());
            ps.setString(3, user.getSecondName());
            ps.setDate(4, user.getBirthdate() != null ? java.sql.Date.valueOf(user.getBirthdate()) : null);
            ps.setString(5, user.getBiography());
            ps.setString(6, user.getCity());
            ps.setString(7, user.getPassword());
            return ps;
        });
        
        return userId;
    }

    public Optional<User> findById(UUID id) {
        String sql = "SELECT id, first_name, second_name, birthdate, biography, city " +
                    "FROM users WHERE id = ?";
        
        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, id);
            return Optional.ofNullable(user);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public boolean validatePassword(UUID userId, String password) {
        String sql = "SELECT EXISTS(SELECT 1 FROM users WHERE id = ? AND password_hash = crypt(?, password_hash))";
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, userId, password));
    }

    public boolean existsById(UUID id) {
        String sql = "SELECT EXISTS(SELECT 1 FROM users WHERE id = ?)";
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, id));
    }

    public void update(User user) {
        String sql = "UPDATE users SET first_name = ?, second_name = ?, birthdate = ?, " +
                    "biography = ?, city = ? WHERE id = ?";
        
        jdbcTemplate.update(sql,
            user.getFirstName(),
            user.getSecondName(),
            user.getBirthdate() != null ? java.sql.Date.valueOf(user.getBirthdate()) : null,
            user.getBiography(),
            user.getCity(),
            user.getId()
        );
    }

    public void deleteById(UUID id) {
        String sql = "DELETE FROM users WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }
}