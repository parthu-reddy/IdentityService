package com.fooddelivery.identity.controller;

import com.fooddelivery.identity.config.SecurityConfig;
import com.fooddelivery.identity.dto.AuthRequests.SessionResponse;
import com.fooddelivery.identity.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

@WebMvcTest(AuthController.class)
@ContextConfiguration(classes={AuthController.class,SecurityConfig.class,com.fooddelivery.common.exception.GlobalExceptionHandler.class})
class OneLoginHttpSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean AuthService auth;
    @MockBean com.fooddelivery.common.security.SecurityContextFilter identityFilter;
    @BeforeEach void filters() throws Exception { com.fooddelivery.common.test.MockIdentityFilterSupport.passThrough(identityFilter); }
    @Test void anonymousJsonOtpAndSessionWorkWithNoPortalHeader() throws Exception {
        mvc.perform(post("/api/v1/auth/otp").contentType(MediaType.APPLICATION_JSON).content("{\"phoneNumber\":\"8999000001\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        when(auth.createSession(any())).thenReturn(new SessionResponse("fixture",true));
        mvc.perform(post("/api/v1/auth/session").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"8999000001\",\"otp\":\"123456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.newUser").value(true));
    }
    @Test void protectedOperationsAndOldInternalLoginCannotBeAnonymous() throws Exception {
        for(var path:List.of("/api/v1/auth/logout","/api/v1/auth/session/refresh","/api/v1/auth/admin-session/otp","/api/v1/auth/admin-session","/api/v1/internal/auth/initiate"))
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/sessions")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/otp")).andExpect(status().isForbidden());
        verifyNoInteractions(auth);
    }
    @Test void refreshUsesTheAuthenticatedPersonAndSession() throws Exception {
        var id=UUID.randomUUID();var sid=UUID.randomUUID().toString();
        var principal=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(id.toString(),null,List.of());
        principal.setDetails(Map.of("sessionId",sid));when(auth.refresh(id,sid)).thenReturn(new SessionResponse("fixture",false));
        mvc.perform(post("/api/v1/auth/session/refresh").with(authentication(principal))).andExpect(status().isOk());
        verify(auth).refresh(id,sid);
    }
}
