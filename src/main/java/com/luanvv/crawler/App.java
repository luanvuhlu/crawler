package com.luanvv.crawler;

import com.luanvv.crawler.core.Crawler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class App {
    public static void main(String[] args) {
        try {
            Crawler.run();
        } catch (Exception e) {
            log.error("Crawler failed", e);
            System.exit(1);
        }
    }
}
