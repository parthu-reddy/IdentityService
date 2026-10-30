package com.fooddelivery.identity.service;

/**
 * OTP namespaces are authentication purposes, independent of product portals. DINE_IN is a guest's consent to one
 * waiter-placed order session; it is issued and checked by DineInConsentService and never signs anyone in.
 */
public enum OtpPurpose { LOGIN, ADMIN, DINE_IN }
