package com.luanvv.crawler.core;

import java.util.concurrent.Callable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class Retryer {
    private final Config.Retries cfg;

    public <T> T runWithRetry(String opName, Callable<T> callable) throws Exception {
        long delay = Math.max(100, cfg.getBackoffMs());
        int attempts = 0;
        Exception last = null;
        while (attempts < cfg.getMaxAttempts()) {
            attempts++;
            try {
                return callable.call();
            } catch (Exception e) {
                last = e;
                log.warn("{} failed on attempt {}/{}: {}", opName, attempts, cfg.getMaxAttempts(), e.toString());
                if (attempts >= cfg.getMaxAttempts()) break;
                Thread.sleep(delay);
                delay = Math.min(cfg.getMaxBackoffMs(), delay * 2);
            }
        }
        throw last != null ? last : new RuntimeException(opName + " failed");
    }
}
