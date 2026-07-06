---
name: identity-service-api
description: API reference and integration guide for the IdentityService. Use this when building auth flows or when other microservices need to create user identities.
---

# IdentityService API Reference

## Public Endpoints (via ApiGateway)

### 1. Send OTP
`POST /api/v1/auth/send-otp`
- **Payload**:
  ```json
  { "phoneNumber": "+1234567890" }
  ```

### 2. Verify OTP
`POST /api/v1/auth/verify-otp`
- **Payload**:
  ```json
  { "phoneNumber": "+1234567890", "otp": "123456" }
  ```
- **Response**: Returns the JWT token.

## Internal Endpoints (Protected by ApiGateway)

### 3. Create Internal User
`POST /api/v1/internal/users/create`
- **Headers**: Must include `X-User-Id` (propagated by Gateway) and `X-Calling-Service` (propagated by `FeignSecurityInterceptor`).
- **Payload**:
  ```json
  {
    "userId": "uuid",
    "phoneNumber": "+1234567890"
  }
  ```
- **Note**: The IdentityService automatically assigns `ROLE_RESTAURANT_OWNER` or `ROLE_DELIVERY_EXECUTIVE` depending on whether `X-Calling-Service` is `restaurant-service` or `delivery-service`.
