package ru.otus.study.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public class User {
    
    @JsonProperty("id")
    private UUID id;
    
    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
    @JsonProperty("first_name")
    private String firstName;
    
    @NotBlank(message = "Second name is required")
    @Size(min = 1, max = 100, message = "Second name must be between 1 and 100 characters")
    @JsonProperty("second_name")
    private String secondName;
    
    @Past(message = "Birthdate must be in the past")
    @JsonProperty("birthdate")
    private LocalDate birthdate;
    
    @Size(max = 1000, message = "Biography must not exceed 1000 characters")
    @JsonProperty("biography")
    private String biography;
    
    @Size(max = 100, message = "City must not exceed 100 characters")
    @JsonProperty("city")
    private String city;
    
    // Для регистрации/логина (не возвращается в ответах)
    private transient String password;

    // Constructors
    public User() {}

    public User(UUID id, String firstName, String secondName, 
                LocalDate birthdate, String biography, String city) {
        this.id = id;
        this.firstName = firstName;
        this.secondName = secondName;
        this.birthdate = birthdate;
        this.biography = biography;
        this.city = city;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getSecondName() { return secondName; }
    public void setSecondName(String secondName) { this.secondName = secondName; }

    public LocalDate getBirthdate() { return birthdate; }
    public void setBirthdate(LocalDate birthdate) { this.birthdate = birthdate; }

    public String getBiography() { return biography; }
    public void setBiography(String biography) { this.biography = biography; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}