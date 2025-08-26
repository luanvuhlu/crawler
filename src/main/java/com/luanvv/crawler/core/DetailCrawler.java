package com.luanvv.crawler.core;

import com.microsoft.playwright.Page;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DetailCrawler {
    private final Config config;
    private final RateLimiter limiter;
    private final Retryer retryer;
    private final Extractor extractor;
    private final LoginManager loginManager;

    public void crawl(Page page, Config.CrawlerConfig crawlerCfg, OutputWriters writers, String url) throws Exception {
        crawl(page, crawlerCfg, writers, url, null);
    }

    public void crawl(Page page, Config.CrawlerConfig crawlerCfg, OutputWriters writers, String url, String uniqueId) throws Exception {
        String detailUrl = url;
        
        // If no URL provided and crawler has a URL, use it
        if (detailUrl == null && crawlerCfg.getUrl() != null && !crawlerCfg.getUrl().isBlank()) {
            detailUrl = config.getBaseUrl() + crawlerCfg.getUrl();
        }
        
        if (detailUrl == null) {
            log.warn("No URL available for detail crawler '{}'", crawlerCfg.getId());
            return;
        }

        final String navigateUrl = detailUrl;
        limiter.acquire();
        log.info("Navigate detail: {}", navigateUrl);
        
        retryer.runWithRetry("navigate-detail", () -> {
            try {
                page.navigate(navigateUrl);
                page.waitForLoadState();
            } catch (Exception e) {
                if (config.getRetries() != null && config.getRetries().isReloginOnFail()) {
                    loginManager.login(page);
                }
                throw e;
            }
            return true;
        });

        Map<String, Object> record = new HashMap<>();
        record.put("url", navigateUrl);
        if (uniqueId != null) {
            record.put("_parentId", uniqueId);
        }
        
        // Extract all fields using the standardized method
        Map<String, Object> extractedFields = extractor.extract(page, crawlerCfg);
        record.putAll(extractedFields);

        // Generate filename
        String filename;
        if (uniqueId != null) {
            filename = "detail_" + crawlerCfg.getId() + "_" + uniqueId;
        } else {
            filename = UrlUtils.sanitizeForFilename(navigateUrl);
        }
        
        writers.writeForUrl(filename, record);
    }
}