# IdentityService

The IdentityService manages user authentication, authorization, and role management for the entire Food Delivery ecosystem. It handles OTP generation, token issuance, and internal role assignments based on calling context.

## Setup & Build
1. Build the service: `mvn clean install`
2. Run the application: `mvn spring-boot:run`
3. Port: `8084`

## Key Responsibilities
- **Authentication**: Generates and verifies OTPs for login.
- **Token Management**: Issues signed JWTs containing user claims (roles, phone number).
- **Internal Security**: Provides internal APIs (protected by `PreAuthFilter`) for other services to create user records or assign roles implicitly (e.g., automatically granting `ROLE_RESTAURANT_OWNER` when the RestaurantApp registers a new owner).
