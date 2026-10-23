package com.asata.org.oro.config.ratelimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * oro.rate-limits.*: how many requests a user makes, per kind of request (RateLimitInterceptor). Each limit is a bucket
 * of burst requests, refilled at per-minute requests a minute: a user may send burst at once, then per-minute a minute.
 */
@Data
@ConfigurationProperties(prefix = "oro.rate-limits")
public class RateLimitProperties {

    /** false: no limit (never in production) */
    private boolean enabled = true;

    /** kind -> limit; "any" applies to every request of a user, the others on top of it */
    private Map<String, Limit> limits = new LinkedHashMap<>();

    @Data
    public static class Limit {
        private int perMinute;
        private int burst;
    }
}
