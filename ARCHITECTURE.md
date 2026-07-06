# IdentityService Architecture

The IdentityService manages authentication internally via OTP and issues standard JWTs which are then strictly parsed by the ApiGateway for all subsequent requests.

## Detailed Sequence Diagram

```mermaid
sequenceDiagram
    participant UserClient as Client (Mobile/Web)
    participant ApiGateway as ApiGateway
    participant IdentityApp as IdentityService
    participant NotificationApp as NotificationService (Kafka)
    participant OtherApp as Other Service (e.g., RestaurantApp)

    %% Authentication Flow
    note right of UserClient: Login Flow
    UserClient->>ApiGateway: POST /api/v1/auth/send-otp
    ApiGateway->>IdentityApp: Forward Request
    IdentityApp->>IdentityApp: Generate OTP, Cache in Redis
    IdentityApp->>NotificationApp: Publish NotificationEvent (SMS)
    IdentityApp-->>UserClient: 200 OK
    
    UserClient->>ApiGateway: POST /api/v1/auth/verify-otp
    ApiGateway->>IdentityApp: Forward Request
    IdentityApp->>IdentityApp: Validate OTP from Redis
    IdentityApp->>IdentityApp: Generate JWT (Signed with secret)
    IdentityApp-->>UserClient: Return JWT Token
    
    %% Dynamic Role Assignment Flow
    note right of OtherApp: Internal Role Provisioning
    OtherApp->>IdentityApp: Feign: POST /api/v1/internal/users/create (e.g., Register Restaurant)
    IdentityApp->>IdentityApp: Extract X-Calling-Service header
    IdentityApp->>IdentityApp: Assign ROLE_RESTAURANT_OWNER dynamically
    IdentityApp-->>OtherApp: 200 OK
```
