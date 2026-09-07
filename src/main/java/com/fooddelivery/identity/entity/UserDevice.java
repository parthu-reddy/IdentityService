package com.fooddelivery.identity.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_devices")@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@lombok.Data

public class UserDevice {
    @Id
    private String sessionId; // UUID representing the session and Primary Key
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private AppUser user;
    @Column(name = "device_id")
    private String deviceId;
    @Column(name = "device_model")
    private String deviceModel;
    @Column(name = "portal")
    private String portal;
    @CreationTimestamp
    @Column(name = "login_time")
    private LocalDateTime loginTime;


}
