package com.fooddelivery.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import com.fooddelivery.common.enums.RoleName;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_roles")@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@lombok.Builder
@lombok.Data

public class UserRole {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;
    @Column(name = "service_name")
    private String serviceName;
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "role_name")
    private RoleName roleName;
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;


}
