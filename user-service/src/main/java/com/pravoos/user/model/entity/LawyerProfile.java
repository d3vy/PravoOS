package com.pravoos.user.model.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "lawyer_profiles")
public class LawyerProfile {

    @Id
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String fullName;

    @Column(length = 100)
    private String barNumber;

    @Column(length = 255)
    private String specialization;

    @Column(length = 50)
    private String phone;

    public UUID getUserId() { return userId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getBarNumber() { return barNumber; }
    public void setBarNumber(String barNumber) { this.barNumber = barNumber; }

    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
