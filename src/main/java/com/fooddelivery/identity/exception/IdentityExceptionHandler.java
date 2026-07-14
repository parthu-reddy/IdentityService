package com.fooddelivery.identity.exception;

import com.fooddelivery.common.dto.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdentityExceptionHandler {

    @ExceptionHandler(MaxSessionsReachedException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleMaxSessionsReachedException(MaxSessionsReachedException ex) {
        Map<String, Object> data = new HashMap<>();
        data.put("activeSessions", ex.getActiveSessions());
        
        ApiResponse<Map<String, Object>> response = ApiResponse.<Map<String, Object>>builder()
                .success(false)
                .message(ex.getMessage())
                .data(data)
                .build();
                
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
}
