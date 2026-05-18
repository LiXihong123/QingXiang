package com.qingxiang.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    //开始时间戳
    private static final long BEGIN_TIMESTAMP = 1735689600;
    //序列号位数，32位，因此时间戳向左移32位
    private static final int COUNT_BITS = 32;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix){
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowEpochSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowEpochSecond - BEGIN_TIMESTAMP;
        //2.生成序列号
        //2.1.获取当前日期
        String day = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.2.increment实现自增长
        long count = stringRedisTemplate.opsForValue().increment("incr:" + keyPrefix + ":" + day);

        //3.位运算拼接key,返回
        return timestamp << COUNT_BITS | count;
    }


}
