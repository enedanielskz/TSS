package ro.unibuc.hello.model;

import java.time.Instant;
import java.util.List;

import lombok.*;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import ro.unibuc.hello.dto.user.UserResponseDTO;
import ro.unibuc.hello.enums.Role;

@Document(collection = "users")
public class User {
    @Id
    private String id;
    private String firstName;
    private String lastName;
    @Indexed(unique = true)
    private String mail;
    @Indexed(unique = true)
    private String phoneNumber;
    private String passwordHash;
    private Instant createdAt;
    private int reviewsNumber;
    private int ratingsSum;
    private Double avgRating;
    private List<Role> roles;

    public User() {}

    public User(String firstname, String lastName, String mail, String phoneNumber, List<Role> roles) {
        this.firstName = firstname;
        this.lastName = lastName;
        this.mail = mail;
        this.phoneNumber = phoneNumber;
        this.createdAt = Instant.now();
        this.roles = roles;
        this.avgRating = (Double) 0.0;
    }   

    public UserResponseDTO fromEntity() {
        UserResponseDTO dto = new UserResponseDTO(
            this.getFirstName(),
            this.getLastName(),
            this.getMail(),
            this.getPhoneNumber(),
            this.getCreatedAt(),
            this.getAvgRating()
        );
        
        return dto;
    }   

    public UserResponseDTO toDTO() {
        UserResponseDTO dto = new UserResponseDTO(
            this.getFirstName(),
            this.getLastName(),
            this.getMail(),
            this.getPhoneNumber(),
            this.getCreatedAt(),    
            this.getAvgRating()
        );
        
        return dto;
    }   

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Double getAvgRating() {
        return avgRating;
    }

    public void setAvgRating(Double rating) {
        this.avgRating = rating;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getMail() {
        return mail;
    }

    public void setMail(String mail) {
        this.mail = mail;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setReviewsNumber(int reviewsNumber) {
        this.reviewsNumber = reviewsNumber;
    }

    public int getReviewsNumber() {
        return reviewsNumber;
    }

    public void setRatingsSum(int ratingsSum) {
        this.ratingsSum = ratingsSum;
    }

    public int getRatingsSum() {
        return ratingsSum;
    }

    public List<Role> getRoles() {
        return roles;
    }

    public void setRole(List<Role> roles) {
        this.roles = roles;
    }
}
