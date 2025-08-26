package com.luanvv.crawler.core;

import com.microsoft.playwright.Page;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Crawler {

    private static final List<String> EXCLUDE_FILES = List.of(
        Paths.get("crawler-configs", "sample.yaml").toString(),
        Paths.get("crawler-configs", "sample.yml").toString()
    );

    public static void run() throws Exception {
        List<Path> configFiles = listConfigFiles()
            .stream()
            .filter(p -> EXCLUDE_FILES.stream().noneMatch(name -> p.toString().endsWith(name)))
            .toList();
        if (configFiles.isEmpty()) {
            log.error("No config files found in crawler-configs");
            return;
        }
        runWithConfigs(configFiles.stream().map(Path::toString).collect(Collectors.toList()));
    }

    private static List<Path> listConfigFiles() throws Exception {
        // 1. Try external directory (for development)
        Path externalDir = Paths.get("src/main/resources", "crawler-configs");
        if (Files.isDirectory(externalDir)) {
            try (var stream = Files.list(externalDir)) {
                return stream
                    .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                    .sorted()
                    .collect(Collectors.toList());
            }
        }

        // 2. Try classpath (for packaged JAR)
        List<Path> result = new java.util.ArrayList<>();
        var resources = Thread.currentThread().getContextClassLoader()
            .getResources("crawler-configs");
        while (resources.hasMoreElements()) {
            var url = resources.nextElement();
            if ("file".equals(url.getProtocol())) {
                Path dir = Paths.get(url.toURI());
                try (var stream = Files.list(dir)) {
                    stream.filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                        .forEach(result::add);
                }
            } else if ("jar".equals(url.getProtocol())) {
                String jarPath = url.getPath().substring(5, url.getPath().indexOf("!"));
                try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarPath)) {
                    jar.stream()
                        .filter(e -> !e.isDirectory() && e.getName().startsWith(
                            "crawler-configs" + "/") &&
                            (e.getName().endsWith(".yaml") || e.getName().endsWith(".yml")))
                        .forEach(e -> result.add(Paths.get(e.getName())));
                }
            }
        }
        return result;
    }

    public static void runWithConfigs(List<String> configPaths) throws Exception {
        for (String configPath : configPaths) {
            Path path = Paths.get(configPath);
            
            if (Files.isDirectory(path)) {
                log.info("Processing config directory: {}", configPath);
                try (var stream = Files.list(path)) {
                    List<Path> configFiles = stream
                        .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                        .sorted()
                        .toList();

                    for (Path configFile : configFiles) {
                        log.info("Processing config file {} in directory {}", configFile, path);
                        Config config = Config.load(configFile);
                        runWithConfig(config);
                    }
                }
            } else if (Files.exists(path)) {
                log.info("Processing config file: {}", configPath);
                Config config = Config.load(path);
                runWithConfig(config);
            } else {
                log.error("Config file or directory not found: {}", configPath);
            }
        }
    }

    private static void runWithConfig(Config config) throws Exception {
        try (BrowserSession session = new BrowserSession(config);
            ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(20);
        ) {
            session.start();
            Page page = session.getPage();
            LoginManager loginManager = new LoginManager(config);
            RateLimiter limiter = new RateLimiter(config.getRateLimit());
            Retryer retryer = new Retryer(config.getRetries());
            OutputWriters writers = new OutputWriters(config.getOutput());
            Extractor extractor = new Extractor(config, config.getOutput().getDir(), executor);

            // Ensure login
            boolean loginOk = retryer.runWithRetry("login", () -> {
                if (loginManager.ensureLoggedIn(page)) return true;
                throw new RuntimeException("login failed");
            });
            if (!loginOk) throw new RuntimeException("Cannot login");

            // Iterate through all crawlers
            for (Config.CrawlerConfig crawlerCfg : config.getCrawlers()) {
                log.info("Starting crawler: {} (type: {})", crawlerCfg.getId(), crawlerCfg.getType());
                
                if ("list".equalsIgnoreCase(crawlerCfg.getType())) {
                    ListCrawler listCrawler = new ListCrawler(config, limiter, retryer, extractor, loginManager);
                    listCrawler.crawl(page, crawlerCfg, writers);
                } else if ("detail".equalsIgnoreCase(crawlerCfg.getType())) {
                    DetailCrawler detailCrawler = new DetailCrawler(config, limiter, retryer, extractor, loginManager);
                    detailCrawler.crawl(page, crawlerCfg, writers, null);
                } else {
                    log.warn("Unknown crawler type '{}' for crawler '{}'", crawlerCfg.getType(), crawlerCfg.getId());
                }
            }
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("Crawling completed successfully");
        }
    }
}