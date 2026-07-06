---
name: understand-identity-service
description: Architectural overview and troubleshooting guide for the IdentityService. Use this to understand role provisioning and the internal security bypasses.
---

# Understand IdentityService

The IdentityService is the source of truth for user authentication and authorization. It is the only service that has access to the JWT signing secret.

## Architecture & Integration

- **Decoupled Verification**: Once IdentityService issues a JWT, it does not need to verify it again. The `ApiGateway` performs all stateless verification using the shared `jwt.secret`.
- **Dynamic Role Provisioning**: To avoid cross-domain tight coupling, IdentityService doesn't expose a `/grant-role` endpoint. Instead, the `InternalUserController` dynamically assigns roles (`ROLE_RESTAURANT_OWNER`, `ROLE_DELIVERY_EXECUTIVE`) based on the `X-Calling-Service` header provided by the `FeignSecurityInterceptor` of the calling domain.

## Troubleshooting

- **Invalid Token / Signature Failed**: Ensure the `jwt.secret` property in `identity-service.yml` perfectly matches the one in `api-gateway.yml`.
- **User Not Getting Roles**: If a restaurant registers but cannot access restaurant APIs, check the IdentityService logs. Ensure `RestaurantApplication` is sending `X-Calling-Service: restaurant-service` in the internal Feign call.
- **OTP Not Received**: Check the Kafka consumer logs in `NotificationService`. `IdentityService` only publishes the event; it does not dispatch the SMS itself.
