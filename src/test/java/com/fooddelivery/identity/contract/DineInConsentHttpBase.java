package com.fooddelivery.identity.contract;

import com.fooddelivery.common.audit.AuditTrail;
import com.fooddelivery.common.contract.PlatformJson;
import com.fooddelivery.common.exception.GlobalExceptionHandler;
import com.fooddelivery.identity.dinein.InMemoryDineInConsentStore;
import com.fooddelivery.identity.dinein.controller.DineInConsentExceptionHandler;
import com.fooddelivery.identity.dinein.controller.InternalDineInConsentController;
import com.fooddelivery.identity.dinein.port.DineInConsent;
import com.fooddelivery.identity.dinein.service.DineInConsentService;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.port.CachePort;
import com.fooddelivery.identity.port.EventPublisherPort;
import com.fooddelivery.identity.repository.UserRepository;
import com.fooddelivery.identity.service.UserProvisioner;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.*;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real controller, service, provisioner and advice over an in-memory consent store; contracts/dinein. */
public abstract class DineInConsentHttpBase {
    static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    static final UUID OUTLET = UUID.fromString("44444444-4444-4444-4444-444444444444");
    static final UUID SESSION = UUID.fromString("55555555-5555-5555-5555-555555555555");
    static final UUID WAITER = UUID.fromString("66666666-6666-6666-6666-666666666666");
    static final UUID GUEST = UUID.fromString("77777777-7777-7777-7777-777777777777");
    /** Whose per-phone cap is already spent this hour. */
    static final String CAPPED_PHONE = "8000000999";

    @BeforeEach
    public void setup() {
        var store = new InMemoryDineInConsentStore();
        store.save(consent("a1111111-1111-1111-1111-111111111111", NOW.plusSeconds(300)), Duration.ofMinutes(20));
        store.save(consent("a2222222-2222-2222-2222-222222222222", NOW.plusSeconds(300)), Duration.ofMinutes(20));
        store.save(consent("a3333333-3333-3333-3333-333333333333", NOW.minusSeconds(1)), Duration.ofMinutes(20));
        var cache = mock(CachePort.class);
        when(cache.increment(anyString(), anyLong())).thenAnswer(i -> ((String) i.getArgument(0)).endsWith(CAPPED_PHONE) ? 6L : 1L);
        var users = mock(UserRepository.class);
        var guest = AppUser.builder().id(GUEST).phoneNumber("8000000123").isActive(true).build();
        when(users.findByPhoneNumber("8000000123")).thenReturn(Optional.of(guest));
        when(users.lockById(GUEST)).thenReturn(Optional.of(guest));
        var service = new DineInConsentService(store, cache, mock(EventPublisherPort.class), new UserProvisioner(users),
                mock(AuditTrail.class), Clock.fixed(NOW, ZoneOffset.UTC));
        RestAssuredMockMvc.standaloneSetup(MockMvcBuilders.standaloneSetup(new InternalDineInConsentController(service))
                .setControllerAdvice(new DineInConsentExceptionHandler(), new GlobalExceptionHandler())
                .setMessageConverters(PlatformJson.messageConverters()));
    }

    static DineInConsent consent(String id, Instant expiresAt) {
        return new DineInConsent(UUID.fromString(id), "8000000123", OUTLET, SESSION, WAITER, "482913", expiresAt);
    }
}
