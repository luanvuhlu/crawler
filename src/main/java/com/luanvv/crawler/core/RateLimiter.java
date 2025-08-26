package com.luanvv.crawler.core;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RateLimiter {
    private final Bucket bucket;

    public RateLimiter(Config.RateLimit cfg) {
        double permitsPerSecond = Math.max(0.1, cfg.getPermitsPerSecond());
        int burst = Math.max(cfg.getBurst(), 1);
        long tokensPerSecond = Math.max(1, Math.round(permitsPerSecond));
        Bandwidth limit = Bandwidth.builder()
                .capacity(burst)
                .refillGreedy(tokensPerSecond, Duration.ofSeconds(1))
                .build();
        bucket = Bucket.builder().addLimit(limit).build();
    }

    public void acquire() {
        try {
            bucket.asBlocking().consume(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for rate limiter", e);
        }
    }
}
