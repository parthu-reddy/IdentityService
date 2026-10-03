package com.fooddelivery.identity.organisation.controller;

import com.fooddelivery.identity.organisation.service.OrganisationRateLimitException;
import com.fooddelivery.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.core.annotation.Order;

@RestControllerAdvice @Order(-1)
public class OrganisationExceptionHandler {
    @ExceptionHandler(OrganisationRateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> rateLimited(OrganisationRateLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfter()))
            .body(ApiResponse.error(ex.getMessage()));
    }
}
