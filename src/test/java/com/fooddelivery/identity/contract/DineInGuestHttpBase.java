package com.fooddelivery.identity.contract;

import com.fooddelivery.common.audit.AuditTrail;
import com.fooddelivery.common.contract.PlatformJson;
import com.fooddelivery.common.exception.GlobalExceptionHandler;
import com.fooddelivery.identity.dinein.controller.DineInGuestExceptionHandler;
import com.fooddelivery.identity.dinein.controller.InternalDineInGuestController;
import com.fooddelivery.identity.dinein.service.DineInGuestService;
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

/** Real controller, service, provisioner and advice; contracts/dinein. */
public abstract class DineInGuestHttpBase {
    static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");
    static final UUID GUEST = UUID.fromString("77777777-7777-7777-7777-777777777777");
    static final UUID BLOCKED = UUID.fromString("88888888-8888-8888-8888-888888888888");
    /** Whose per-phone cap is already spent this hour. */
    static final String CAPPED_PHONE = "8000000999";

    @BeforeEach
    public void setup() {
        var cache = mock(CachePort.class);
        when(cache.increment(anyString(), anyLong())).thenAnswer(i -> ((String) i.getArgument(0)).endsWith(CAPPED_PHONE) ? 6L : 1L);
        var users = mock(UserRepository.class);
        var guest = AppUser.builder().id(GUEST).phoneNumber("8000000123").isActive(true).build();
        when(users.findByPhoneNumber("8000000123")).thenReturn(Optional.of(guest));
        when(users.lockById(GUEST)).thenReturn(Optional.of(guest));
        var blocked = AppUser.builder().id(BLOCKED).phoneNumber("8000000888").isActive(false).build();
        when(users.findByPhoneNumber("8000000888")).thenReturn(Optional.of(blocked));
        when(users.lockById(BLOCKED)).thenReturn(Optional.of(blocked));
        var service = new DineInGuestService(cache, mock(EventPublisherPort.class), new UserProvisioner(users),
                mock(AuditTrail.class), Clock.fixed(NOW, ZoneOffset.UTC));
        RestAssuredMockMvc.standaloneSetup(MockMvcBuilders.standaloneSetup(new InternalDineInGuestController(service))
                .setControllerAdvice(new DineInGuestExceptionHandler(), new GlobalExceptionHandler())
                .setMessageConverters(PlatformJson.messageConverters()));
    }
}
