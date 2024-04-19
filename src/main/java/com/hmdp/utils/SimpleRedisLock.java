package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private String name;

    private static final String KEY_PREFIX = "lock:";

    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标识
        long id = Thread.currentThread().getId();
        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue().
                setIfAbsent(KEY_PREFIX + name, id + "", timeoutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        // 释放锁
        stringRedisTemplate.delete(KEY_PREFIX + name);
    }
}
