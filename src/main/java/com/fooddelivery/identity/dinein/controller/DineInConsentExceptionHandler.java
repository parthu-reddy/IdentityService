package com.fooddelivery.identity.dinein.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.identity.dinein.service.DineInConsentException;

import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(-1)
public class DineInConsentExceptionHandler {
    @ExceptionHandler(DineInConsentException.class)
    public ResponseEntity<ApiResponse<Void>> refused(DineInConsentException ex) {
        return ResponseEntity.status(ex.status()).body(ApiResponse.error(ex.getMessage(), ex.code()));
    }
}
