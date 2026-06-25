package com.example.judicialappraisal.caseinfo.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class SerialNoGenerator {

    private final StringRedisTemplate redisTemplate;
    private static final String SERIAL_PREFIX = "JA";
    private static final String REDIS_KEY_PREFIX = "serial_no:";

    public SerialNoGenerator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String generateSerialNo() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String redisKey = REDIS_KEY_PREFIX + dateStr;
        
        Long sequence = redisTemplate.opsForValue().increment(redisKey);
        // expire key after 24 hours to save space, though not strictly required
        if (sequence != null && sequence == 1) {
            redisTemplate.expire(redisKey, java.time.Duration.ofHours(24));
        }
        
        return String.format("%s-%s-%04d", SERIAL_PREFIX, dateStr, sequence);
    }
}
