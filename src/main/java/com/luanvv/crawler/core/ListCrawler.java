package com.luanvv.crawler.core;

import com.luanvv.crawler.core.Config.CrawlerConfig;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ListCrawler {
    private final Config config;
    private final RateLimiter limiter;
    private final Retryer retryer;
    private final Extractor extractor;
    private final LoginManager loginManager;
    private String baseUrl;

    public void crawl(Page page, Config.CrawlerConfig crawlerCfg, OutputWriters writers) throws Exception {
        if (crawlerCfg.getUrl() == null || crawlerCfg.getUrl().isBlank()) {
            log.info("Skipping crawler '{}' - no URL defined", crawlerCfg.getId());
            return;
        }

        baseUrl = config.getBaseUrl() + crawlerCfg.getUrl();
        String currentPageText = "";
        int pageCount = 0;
        int startPage = crawlerCfg.getProperties().getStartPage();
        while (true) {
            var startTime = System.currentTimeMillis();
            pageCount++;
            limiter.acquire();

            final String navigateUrl = baseUrl;
            log.info("Navigate list page {}: {}", pageCount, navigateUrl);

            retryer.runWithRetry("navigate-list", () -> {
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
            // Skip pages until startPage
            if (pageCount < startPage) {
                log.info("Skipping page {} (startPage={})", pageCount, startPage);
            } else {
                // Check if we're on the same page (pagination detection)
                if (crawlerCfg.getProperties() != null && crawlerCfg.getProperties().getCurrentPageSelector() != null) {
                    try {
                        String newPageText = page.locator(crawlerCfg.getProperties().getCurrentPageSelector()).first().innerText().trim();
                        if (currentPageText.equals(newPageText) && pageCount > 1) {
                            log.info("Same page detected, stopping pagination");
                            break;
                        }
                        currentPageText = newPageText;
                    } catch (Exception e) {
                        log.warn("Could not read current page indicator: {}", e.getMessage());
                    }
                }

                // Extract items from current page
                if (crawlerCfg.getProperties() != null && crawlerCfg.getProperties().getItemSelectors() != null) {
                    String itemSelector = crawlerCfg.getProperties().getItemSelectors();
                    if (crawlerCfg.getRootSelector() != null) {
                        itemSelector = crawlerCfg.getRootSelector() + " " + itemSelector;
                    }

                    var items = page.locator(itemSelector).all();
                    log.info("Found {} items on page {}", items.size(), pageCount);

                    for (int index = 0; index < items.size(); index++) {
                        var item = items.get(index);
                        crawlItem(page, crawlerCfg, writers, item, index);
                    }
                    log.info("Completed crawling all {} items on page {}", items.size(), pageCount);
                }
            }

            if (!nextPage(crawlerCfg, page)) {
                break;
            }
            var duration = System.currentTimeMillis() - startTime;
            log.info("Crawled items on page {} in {} ms", pageCount, duration);
        }
    }

    private boolean nextPage(CrawlerConfig crawlerCfg, Page page) {
        // Check for next page
        if (crawlerCfg.getProperties() == null || crawlerCfg.getProperties().getNextSelector() == null) {
            log.info("No pagination configured, stopping after first page");
            return false;
        }
        try {
            var nextButton = page.locator(crawlerCfg.getProperties().getNextSelector()).first();
            if (!nextButton.isVisible()) {
                log.info("No more pages available");
                return false;
            }

            limiter.acquire();
            nextButton.click();
            page.waitForLoadState();
            Thread.sleep(1000); // Wait for page to load
            baseUrl = page.url(); // Update baseUrl in case of URL change
            return true;
        } catch (Exception e) {
            log.info("Could not navigate to next page: {}", e.getMessage());
            return false;
        }
    }

    private void crawlItem(Page page, CrawlerConfig crawlerCfg, OutputWriters writers, Locator item,
        int index) {
        Map<String, Object> listRecord = extractListFields(page, item, crawlerCfg, index);

        // Get detail link if available
        String detailUrl = null;
        if (crawlerCfg.getProperties().getDetailLinkSelector() != null) {
            try {
                var linkLocator = item.locator(crawlerCfg.getProperties().getDetailLinkSelector()).first();
                if (linkLocator.isVisible()) {
                    String href = linkLocator.getAttribute("href");
                    if (href != null && !href.isBlank()) {
                        detailUrl = UrlUtils.toAbsolute(config.getBaseUrl(), href).toString();
                    }
                }
            } catch (Exception e) {
                log.warn("Could not extract detail link for item {}: {}", index, e.getMessage());
            }
        }

        // Save list item data (synchronous)
        String uniqueId = getUniqueId(listRecord, crawlerCfg.getProperties().getUniqueKey(), index);
        writers.writeForUrl("list_" + crawlerCfg.getId() + "_" + uniqueId, listRecord);

        // Add detail crawling task if available (asynchronous)
        if (detailUrl != null && crawlerCfg.getProperties().getCrawlId() != null) {
            CrawlerConfig detailCrawler = config.findCrawlerById(crawlerCfg.getProperties().getCrawlId());
            if (detailCrawler != null) {
                crawlDetailItem(page, detailCrawler, detailUrl, uniqueId, writers);
            } else {
                log.warn("Detail crawler '{}' not found", crawlerCfg.getProperties().getCrawlId());
            }
        }
    }

    private Map<String, Object> extractListFields(Page page, Locator itemLocator, Config.CrawlerConfig crawlerCfg, int index) {
        Map<String, Object> record = new HashMap<>();
        if (crawlerCfg.getFields() != null) {
            for (Config.Field field : crawlerCfg.getFields()) {
                try {
                    Object value = extractFieldFromItem(itemLocator, field);
                    record.put(field.getName(), value);
                } catch (Exception e) {
                    log.warn("Failed to extract field '{}' from item {}: {}", field.getName(), index, e.getMessage());
                    record.put(field.getName(), null);
                }
            }
        }

        return record;
    }

    private Object extractFieldFromItem(Locator itemLocator, Config.Field field) {
        return extractor.extractFromLocator(itemLocator, field);
    }

    private String getUniqueId(Map<String, Object> record, String uniqueKey, int fallbackIndex) {
        if (uniqueKey != null && record.containsKey(uniqueKey)) {
            Object value = record.get(uniqueKey);
            if (value != null) {
                return UrlUtils.sanitizeForFilename(value.toString());
            }
        }
        return "item_" + fallbackIndex;
    }

    private void crawlDetailItem(Page page, Config.CrawlerConfig detailCrawler, String detailUrl, String uniqueId, OutputWriters writers) {
        Page detailPage = null;
        try {
            // Create new tab/page for detail crawling
            detailPage = page.context().newPage();

            // Copy authentication context from original page
//            copyAuthenticationContext(page, detailPage);

            DetailCrawler detailCrawlerInstance = new DetailCrawler(config, limiter, retryer, extractor, loginManager);
            detailCrawlerInstance.crawl(detailPage, detailCrawler, writers, detailUrl, uniqueId);
        } catch (Exception e) {
            log.error("Failed to crawl detail page {}: {}", detailUrl, e.getMessage());
        } finally {
            if (detailPage != null) {
                try {
                    detailPage.close();
                } catch (Exception e) {
                    log.warn("Failed to close detail page: {}", e.getMessage());
                }
            }
            page.bringToFront();
        }
    }

    private void copyAuthenticationContext(Page sourcePage, Page targetPage) {
        try {
            // Copy cookies
            var cookies = sourcePage.context().cookies();
            if (!cookies.isEmpty()) {
                targetPage.context().addCookies(cookies);
            }

            // Copy localStorage and sessionStorage
            Object localStorage = sourcePage.evaluate("() => JSON.stringify(localStorage)");
            Object sessionStorage = sourcePage.evaluate("() => JSON.stringify(sessionStorage)");
            
            if (localStorage != null && !localStorage.toString().equals("{}")) {
                targetPage.addInitScript("(storage => { " +
                    "if (storage) { " +
                        "const data = JSON.parse(storage); " +
                        "for (const key in data) { " +
                            "localStorage.setItem(key, data[key]); " +
                        "} " +
                    "} " +
                "})('" + localStorage.toString().replace("'", "\\'") + "');");
            }

            if (sessionStorage != null && !sessionStorage.toString().equals("{}")) {
                targetPage.addInitScript("(storage => { " +
                    "if (storage) { " +
                        "const data = JSON.parse(storage); " +
                        "for (const key in data) { " +
                            "sessionStorage.setItem(key, data[key]); " +
                        "} " +
                    "} " +
                "})('" + sessionStorage.toString().replace("'", "\\'") + "');");
            }

            log.debug("Authentication context copied to detail page");
        } catch (Exception e) {
            log.warn("Failed to copy authentication context: {}", e.getMessage());
        }
    }
}