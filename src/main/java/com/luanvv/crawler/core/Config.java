package com.luanvv.crawler.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Config {
    private boolean headless = true;
    private int parallelism = 10;
    private String baseUrl;
    private Login login;
    private RateLimit rateLimit;
    private Retries retries;
    private Output output;
    private List<CrawlerConfig> crawlers;

    @Data
    public static class Login {
        private String url;
        private String usernameSelector;
        private String passwordSelector;
        private String submitSelector;
        private String usernameEnv;
        private String passwordEnv;
        private String loggedInCheckSelector;
        private long timeoutMs = 20000;
    }

    @Data
    public static class RateLimit {
        private double permitsPerSecond = 1.0;
        private int burst = 2;
    }

    @Data
    public static class Retries {
        private int maxAttempts = 3;
        private long backoffMs = 1000;
        private long maxBackoffMs = 8000;
        private boolean reloginOnFail = true;
    }

    @Data
    public static class Output {
        private String dir = "data";
        private boolean json = true;
        private boolean csv = true;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CrawlerConfig {
        private String id;
        private String type; // list or detail
        private String url;
        private String rootSelector;
        private Properties properties;
        private List<Field> fields;
    }

    @Data
    public static class Properties {
        private String itemSelectors;
        private String detailLinkSelector;
        private String crawlId;
        private String uniqueKey;
        private String nextSelector;
        private String currentPageSelector;
        private int startPage = 1;
    }

    @Data
    public static class Field {
        private String id;
        private String name;
        private String selector;
        private String type; // text, html, select, checkbox, boolean, image, label
        private boolean multiple = false;
        private List<BooleanValue> values;
        private List<PreAction> preActions;
    }

    @Data
    public static class BooleanValue {
        private String name;
        private String value;
    }

    @Data
    public static class PreAction {
        private String id;
        private String script;
    }

    public static Config load(Path path) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (InputStream in = Files.newInputStream(path)) {
            return mapper.readValue(in, Config.class);
        }
    }

    public static Config load(String configPath) throws IOException {
        return load(Path.of(configPath));
    }

    public static Config loadDefault() throws IOException {
        // Try multiple default locations
        String[] defaultPaths = {
            "src/main/resources/crawler-config.yaml",
            "src/main/resources/crawler-config.yml",
            "crawler-config.yaml",
            "crawler-config.yml",
            "config/crawler-config.yaml",
            "config/crawler-config.yml"
        };
        
        for (String defaultPath : defaultPaths) {
            Path path = Path.of(defaultPath);
            if (Files.exists(path)) {
                log.info("Using default config file: {}", path);
                return load(path);
            }
        }
        
        throw new IOException("No default config file found in any of these locations: " + 
                            String.join(", ", defaultPaths));
    }

    public CrawlerConfig findCrawlerById(String id) {
        return crawlers.stream()
                .filter(c -> id.equals(c.getId()))
                .findFirst()
                .orElse(null);
    }
}