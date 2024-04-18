package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;


@Component
public class RedisIdWorker {

    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIME = 1704067200L;

    /**
     * 序列号的位数
     */
    private static final int COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * redis实现全局唯一Id
     * @param keyPrefix 键
     * @return id
     */
    public long nextId(String keyPrefix) {
        // 1.生成时间戳
        long nowTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowTime - BEGIN_TIME;

        // 2.生成序列号(自增，key按照每天进行区分)
        // 2.1得到：年月日
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2key自增长
        Long count = stringRedisTemplate.opsForValue().increment("inc:" + keyPrefix + date);
        // 3.拼接起来

        return timestamp << COUNT_BITS | count;
    }


}
