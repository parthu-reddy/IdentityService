package com.fooddelivery.identity.service;

import com.fooddelivery.common.enums.RoleName;
import com.fooddelivery.identity.entity.UserRole;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The server-owned mapping between a public login choice and an assigned application role.
 *
 * <p>{@code X-Calling-Service} is sent by a browser during OTP login. It can select a portal, but
 * it cannot grant a role during ordinary login. Explicit signup can enroll a customer or partner
 * for onboarding; administrator assignments always require separate provisioning.</p>
 */
public enum AuthPortal {
    CUSTOMER(RoleName.CUSTOMER, "CustomerApplication", true,
            Set.of("customer", "customerapplication", "customer-service")),
    DELIVERY(RoleName.DELIVERY, "DeliveryExecutiveApplication", false,
            Set.of("delivery", "deliveryexecutiveapplication", "delivery-service")),
    RESTAURANT(RoleName.RESTAURANT, "RestaurantApplication", false,
            Set.of("restaurant", "restaurantapplication", "restaurant-service")),
    ADMIN(RoleName.ADMIN, "ADMIN", false,
            Set.of("admin", "adminapplication"));

    private final RoleName role;
    private final String canonicalServiceName;
    private final boolean selfRegistrationAllowed;
    private final Set<String> acceptedServiceNames;

    AuthPortal(RoleName role, String canonicalServiceName, boolean selfRegistrationAllowed,
               Set<String> acceptedServiceNames) {
        this.role = role;
        this.canonicalServiceName = canonicalServiceName;
        this.selfRegistrationAllowed = selfRegistrationAllowed;
        this.acceptedServiceNames = acceptedServiceNames;
    }

    public static AuthPortal fromCallerService(String serviceName) {
        String normalized = normalize(serviceName);
        for (AuthPortal portal : values()) {
            if (portal.acceptedServiceNames.contains(normalized)) {
                return portal;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported login portal");
    }

    static AuthPortal forRole(RoleName role) {
        for (AuthPortal portal : values()) {
            if (portal.role == role) {
                return portal;
            }
        }
        throw new IllegalArgumentException("Unsupported application role: " + role);
    }

    RoleName role() {
        return role;
    }

    String canonicalServiceName() {
        return canonicalServiceName;
    }

    boolean selfRegistrationAllowed() {
        return selfRegistrationAllowed;
    }

    public String sessionServiceName() {
        return name().toLowerCase(Locale.ROOT);
    }

    boolean matches(UserRole assignment) {
        return assignment != null
                && assignment.getRoleName() == role
                && acceptedServiceNames.contains(normalize(assignment.getServiceName()));
    }

    private static String normalize(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return "";
        }
        return serviceName.trim().toLowerCase(Locale.ROOT);
    }
}
