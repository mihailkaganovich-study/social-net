package ru.otus.study.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class RegisterRequest {
    
    @NotBlank(message = "First name is required")
    @JsonProperty("first_name")
    private String firstName;
    
    @NotBlank(message = "Second name is required")
    @JsonProperty("second_name")
    private String secondName;
    
    @Past(message = "Birthdate must be in the past")
    @JsonProperty("birthdate")
    private LocalDate birthdate;
    
    @JsonProperty("biography")
    private String biography;
    
    @JsonProperty("city")
    private String city;
    
    @NotBlank(message = "Password is required")
    @JsonProperty("password")
    private String password;

    // Getters and Setters
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