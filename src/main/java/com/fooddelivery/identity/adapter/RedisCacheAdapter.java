package com.fooddelivery.identity.adapter;

import com.fooddelivery.identity.port.CachePort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public class RedisCacheAdapter implements CachePort {
    private final StringRedisTemplate redisTemplate;

    @Override
    public void put(String key, String value, long expirationMinutes) {
        redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(expirationMinutes));
    }

    @Override
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public Long increment(String key, long expirationMinutes) {
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redisTemplate.expire(key, Duration.ofMinutes(expirationMinutes));
        }
        return value;
    }

    @java.lang.SuppressWarnings("all")
    public RedisCacheAdapter(final StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
}
