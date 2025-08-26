package com.luanvv.crawler.core;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class LoginManager {
    private final Config config;

    public boolean ensureLoggedIn(Page page) {
        try {
            if (isLoggedIn(page)) return true;
            return login(page);
        } catch (Exception e) {
            log.error("Login failed", e);
            return false;
        }
    }

    public boolean isLoggedIn(Page page) {
        try {
            String sel = config.getLogin().getLoggedInCheckSelector();
            if (sel == null || sel.isBlank()) return false;
            return page.locator(sel).first().isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean login(Page page) {
        var login = config.getLogin();
        String username = System.getenv(login.getUsernameEnv());
        String password = System.getenv(login.getPasswordEnv());
        if (username == null || password == null) {
            log.error("Missing credentials in env vars {} / {}", login.getUsernameEnv(), login.getPasswordEnv());
            return false;
        }
        String loginUrl = config.getBaseUrl() + login.getUrl();
        log.info("Navigating to login page: {}", loginUrl);
        page.navigate(loginUrl);
        page.waitForSelector(login.getUsernameSelector(), new Page.WaitForSelectorOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(login.getTimeoutMs()));
        page.fill(login.getUsernameSelector(), username);
        page.fill(login.getPasswordSelector(), password);
        page.click(login.getSubmitSelector());
        try {
            page.waitForSelector(login.getLoggedInCheckSelector(), new Page.WaitForSelectorOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(login.getTimeoutMs()));
            log.info("Logged in successfully");
            return true;
        } catch (Exception e) {
            log.warn("Login check not visible: {}", e.getMessage());
            return false;
        }
    }
}
