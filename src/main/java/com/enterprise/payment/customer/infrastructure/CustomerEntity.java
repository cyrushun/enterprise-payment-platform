package com.enterprise.payment.customer.infrastructure;

import com.enterprise.payment.customer.domain.CustomerStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class CustomerEntity {
    @Id private UUID id;
    @Column(name = "first_name", nullable = false, length = 100) private String firstName;
    @Column(name = "last_name", nullable = false, length = 100) private String lastName;
    @Column(nullable = false, length = 320, unique = true) private String email;
    @Column(name = "phone_number", length = 32) private String phoneNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private CustomerStatus status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected CustomerEntity() {}
    public CustomerEntity(String firstName, String lastName, String email, String phoneNumber) {
        this.id = UUID.randomUUID(); this.firstName = firstName; this.lastName = lastName;
        this.email = email; this.phoneNumber = phoneNumber; this.status = CustomerStatus.ACTIVE;
        this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }
    public UUID getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public CustomerStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
