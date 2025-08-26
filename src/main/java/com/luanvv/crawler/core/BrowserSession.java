package com.luanvv.crawler.core;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BrowserSession implements AutoCloseable {
    private final Config config;
    @Getter private Playwright playwright;
    @Getter private Browser browser;
    @Getter private BrowserContext context;
    @Getter private Page page;

    public void start() {
        playwright = Playwright.create();
        BrowserType chromium = playwright.chromium();
        browser = chromium.launch(new BrowserType.LaunchOptions().setHeadless(config.isHeadless()));
        context = browser.newContext(new Browser.NewContextOptions());
        page = context.newPage();
    }

    @Override
    public void close() {
        if (context != null) context.close();
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }
}
