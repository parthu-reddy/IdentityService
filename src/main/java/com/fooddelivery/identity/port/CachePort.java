package com.fooddelivery.identity.port;

public interface CachePort {
    void put(String key, String value, long expirationMinutes);
    String get(String key);
    void delete(String key);
}
