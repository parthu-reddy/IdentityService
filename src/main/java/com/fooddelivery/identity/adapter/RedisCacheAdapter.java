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
        var script=new org.springframework.data.redis.core.script.DefaultRedisScript<Long>(
            "local n=redis.call('INCR',KEYS[1]);if n==1 then redis.call('PEXPIRE',KEYS[1],ARGV[1]) end;return n",Long.class);
        return redisTemplate.execute(script,java.util.List.of(key),Long.toString(Duration.ofMinutes(expirationMinutes).toMillis()));
    }

    @Override
    public boolean consumeOtp(String key,String expected) {
        var script=new org.springframework.data.redis.core.script.DefaultRedisScript<Long>(
            "if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1]) end;return 0",Long.class);
        return Long.valueOf(1).equals(redisTemplate.execute(script,java.util.List.of(key),expected));
    }

    @Override
    public void putVersions(java.util.Map<String,String> versions, Duration ttl) {
        byte[] script=("local old=redis.call('GET',KEYS[1]); "
            +"if not old or tonumber(ARGV[1])>=tonumber(old) then "
            +"redis.call('SET',KEYS[1],ARGV[1],'EX',ARGV[2]);return 1 end;return 0")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            versions.forEach((key,value) -> connection.scriptingCommands().eval(script,
                org.springframework.data.redis.connection.ReturnType.INTEGER,1,
                key.getBytes(java.nio.charset.StandardCharsets.UTF_8),value.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Long.toString(Math.max(1,ttl.toSeconds())).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return null;
        });
    }

    @java.lang.SuppressWarnings("all")
    public RedisCacheAdapter(final StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
}
