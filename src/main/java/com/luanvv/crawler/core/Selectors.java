package com.luanvv.crawler.core;

public class Selectors {
    public static boolean isVisible(com.microsoft.playwright.Page page, String selector) {
        try { return page.locator(selector).first().isVisible(); } catch (Exception e) { return false; }
    }
}
