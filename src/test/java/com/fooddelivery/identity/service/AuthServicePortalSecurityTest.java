package com.fooddelivery.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.enums.RoleName;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.entity.UserRole;
import com.fooddelivery.identity.port.CachePort;
import com.fooddelivery.identity.port.EventPublisherPort;
import com.fooddelivery.identity.repository.UserRepository;
import com.fooddelivery.identity.repository.UserRoleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class AuthServicePortalSecurityTest {

    private static final String PHONE = "9000000001";
    private static final String OTP = "123456";

    private final UserRepository users = mock(UserRepository.class);
    private final UserRoleRepository roles = mock(UserRoleRepository.class);
    private final EventPublisherPort events = mock(EventPublisherPort.class);
    private final CachePort cache = mock(CachePort.class);
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, roles, events, cache, new ObjectMapper());
        ReflectionTestUtils.setField(service, "privateKeyResource", new ClassPathResource("certs/private.pem"));
        ReflectionTestUtils.setField(service, "publicKeyResource", new ClassPathResource("certs/public.pem"));
        ReflectionTestUtils.setField(service, "jwtExpirationMs", 3_600_000L);
        service.init();
        when(cache.get(anyString())).thenAnswer(invocation ->
                invocation.getArgument(0, String.class).startsWith("OTP:") ? OTP : null);
    }

    @Test
    @org.junit.jupiter.api.Tag("auth-rate-limit")
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "auth.limits.enabled", matches = "true")
    void defaultInitiateLimitStopsOtpStorageAndNotifications() {
        when(cache.increment("RATELIMIT:INITIATE:" + PHONE, 10)).thenReturn(11L);
        var error = assertThrows(IllegalArgumentException.class,
                () -> service.initiateLogin(PHONE, "CUSTOMER"));
        assertEquals("Too many login attempts. Please try again later.", error.getMessage());
        verify(cache, never()).put(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
        org.mockito.Mockito.verifyNoInteractions(events);
    }

    @Test
    @org.junit.jupiter.api.Tag("auth-rate-limit")
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "auth.limits.enabled", matches = "true")
    void defaultVerifyLimitInvalidatesOtpBeforeUserOrSessionCreation() {
        when(cache.increment("RATELIMIT:VERIFY:" + PHONE, 5)).thenReturn(6L);
        var error = assertThrows(IllegalArgumentException.class, () -> verifyOtp("CUSTOMER"));
        assertEquals("Too many failed attempts. Please request a new OTP.", error.getMessage());
        verify(cache).delete("OTP:" + PHONE + ":customer");
        org.mockito.Mockito.verifyNoInteractions(users, roles, events);
        verify(cache, never()).put(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void adminHeaderCannotCreateAUserOrGrantAdmin() {
        when(users.findByPhoneNumber(PHONE)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> verifyOtp("ADMIN"));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(users, never()).save(any());
        verify(roles, never()).save(any());
    }

    @Test
    void deliveryHeaderCannotConvertACustomerIntoARider() {
        AppUser customer = activeUser();
        when(users.findByPhoneNumber(PHONE)).thenReturn(Optional.of(customer));
        when(roles.findByUserId(customer.getId())).thenReturn(List.of(role(customer, RoleName.CUSTOMER,
                "CustomerApplication")));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> verifyOtp("DELIVERY"));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(roles, never()).save(any());
    }

    @Test
    void inactiveAccountCannotReceiveANewToken() {
        AppUser inactive = AppUser.builder().id(UUID.randomUUID()).phoneNumber(PHONE).isActive(false).build();
        when(users.findByPhoneNumber(PHONE)).thenReturn(Optional.of(inactive));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> verifyOtp("CUSTOMER"));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(roles, never()).save(any());
    }

    @Test
    void existingAdminCanAuthenticateWithoutAnyNewRoleAssignment() {
        AppUser admin = activeUser();
        when(users.findByPhoneNumber(PHONE)).thenReturn(Optional.of(admin));
        when(roles.findByUserId(admin.getId())).thenReturn(List.of(role(admin, RoleName.ADMIN, "ADMIN")));

        String token = verifyOtp("ADMIN");

        assertFalse(token.isBlank());
        verify(roles, never()).save(any());
    }

    @Test
    void legacySeededCustomerServiceNameCanStillAuthenticateAsCustomer() {
        AppUser customer = activeUser();
        when(users.findByPhoneNumber(PHONE)).thenReturn(Optional.of(customer));
        when(roles.findByUserId(customer.getId())).thenReturn(List.of(role(customer, RoleName.CUSTOMER,
                "customer-service")));

        String token = verifyOtp("CUSTOMER");

        assertFalse(token.isBlank());
        verify(roles, never()).save(any());
    }

    @Test
    void newCustomerStillGetsTheCustomerRoleOnly() {
        AppUser customer = activeUser();
        when(users.findByPhoneNumber(PHONE)).thenReturn(Optional.empty());
        when(users.save(any(AppUser.class))).thenReturn(customer);
        when(roles.findByUserId(customer.getId())).thenReturn(List.of());

        String token = verifyOtp("CustomerApplication");

        assertFalse(token.isBlank());
        org.mockito.ArgumentCaptor<UserRole> assignment = org.mockito.ArgumentCaptor.forClass(UserRole.class);
        verify(roles).save(assignment.capture());
        assertEquals(RoleName.CUSTOMER, assignment.getValue().getRoleName());
        assertEquals("CustomerApplication", assignment.getValue().getServiceName());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = AuthPortal.class, names = {"CUSTOMER", "DELIVERY", "RESTAURANT"})
    void explicitSignupCreatesOnlyTheSelectedOnboardingRole(AuthPortal portal) {
        AppUser applicant = activeUser();
        when(users.findByPhoneNumber(PHONE)).thenReturn(Optional.empty());
        when(users.save(any(AppUser.class))).thenReturn(applicant);
        when(roles.findByUserId(applicant.getId())).thenReturn(List.of());

        String token = service.registerWithOtp(PHONE, OTP, portal.name(), "test", "macOS", "Chromium", null);

        assertFalse(token.isBlank());
        var assignment = org.mockito.ArgumentCaptor.forClass(UserRole.class);
        verify(roles).save(assignment.capture());
        assertEquals(portal.role(), assignment.getValue().getRoleName());
        assertEquals(portal.canonicalServiceName(), assignment.getValue().getServiceName());
        var claims = io.jsonwebtoken.Jwts.parserBuilder().setSigningKey(
                (java.security.PublicKey) ReflectionTestUtils.getField(service, "publicKey"))
                .build().parseClaimsJws(token).getBody();
        assertEquals(List.of(portal.role().name()), claims.get("roles"));
    }

    @Test
    void explicitSignupCannotProvisionAnAdministrator() {
        var error = assertThrows(ResponseStatusException.class, () ->
                service.registerWithOtp(PHONE, OTP, "ADMIN", "test", "macOS", "Chromium", null));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(users, never()).save(any());
        verify(roles, never()).save(any());
    }

    @Test
    void signupCannotBypassOtpVerification() {
        assertThrows(IllegalArgumentException.class, () ->
                service.registerWithOtp(PHONE, "999999", "DELIVERY", "test", "macOS", "Chromium", null));
        verify(users, never()).save(any());
        verify(roles, never()).save(any());
    }

    @Test
    void signupCannotReactivateAnInactiveAccount() {
        var inactive = AppUser.builder().id(UUID.randomUUID()).phoneNumber(PHONE).isActive(false).build();
        when(users.findByPhoneNumber(PHONE)).thenReturn(Optional.of(inactive));
        var error = assertThrows(ResponseStatusException.class, () ->
                service.registerWithOtp(PHONE, OTP, "RESTAURANT", "test", "macOS", "Chromium", null));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(roles, never()).save(any());
    }

    private String verifyOtp(String serviceName) {
        return service.verifyOtp(PHONE, OTP, serviceName, "test", "macOS", "Chromium", null);
    }

    private static AppUser activeUser() {
        return AppUser.builder().id(UUID.randomUUID()).phoneNumber(PHONE).isActive(true).build();
    }

    private static UserRole role(AppUser user, RoleName role, String serviceName) {
        return UserRole.builder().id(UUID.randomUUID()).user(user).roleName(role).serviceName(serviceName).build();
    }
}
