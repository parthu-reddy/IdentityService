package com.fooddelivery.identity.controller;

import com.fooddelivery.identity.dto.AuthRequests.*;
import com.fooddelivery.identity.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OneLoginControllerAuthorizationTest {
    @Test void onlyTheTwoJsonEntryPointsAreAnonymous() {
        var mapped=Arrays.stream(AuthController.class.getMethods()).filter(m -> m.isAnnotationPresent(PostMapping.class)
            ||m.isAnnotationPresent(GetMapping.class)||m.isAnnotationPresent(DeleteMapping.class)).toList();
        assertEquals(9,mapped.size());
        for (var m:mapped) assertEquals(Set.of("initiateLogin","createSession").contains(m.getName())?"permitAll()":"isAuthenticated()",m.getAnnotation(PreAuthorize.class).value(),m.getName());
        assertEquals("/api/v1/auth",AuthController.class.getAnnotation(RequestMapping.class).value()[0]);
    }
    @Test void refreshUsesVerifiedPrincipalAndSignedSessionDetails() {
        var auth=mock(AuthService.class);ObjectProvider<DevOtpAccessPolicy> provider=mock(ObjectProvider.class);
        var controller=new AuthController(auth,provider);UUID user=UUID.randomUUID();String sid=UUID.randomUUID().toString();
        var caller=new UsernamePasswordAuthenticationToken(user.toString(),null,List.of());caller.setDetails(Map.of("sessionId",sid));
        controller.refresh(caller);verify(auth).refresh(user,sid);
        caller.setDetails(Map.of());var error=assertThrows(ResponseStatusException.class,()->controller.refresh(caller));assertEquals(401,error.getStatusCode().value());
        verifyNoMoreInteractions(auth);
    }
}
