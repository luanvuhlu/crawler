package com.luanvv.crawler.core;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Extractor {
    private final Path imageDir;
    private final Path rootDir;
    private final ExecutorService executor;
    private final Config config;

    public Extractor(Config config, String outputDir, ExecutorService executor) throws IOException {
        this.config = config;
        this.executor = executor;
        this.rootDir = Paths.get(outputDir);
        this.imageDir = rootDir.resolve("images");
        Files.createDirectories(imageDir);
    }

    public Map<String, Object> extract(Page page, Config.CrawlerConfig crawlerCfg) {
        Map<String, Object> out = new HashMap<>();
        if (crawlerCfg.getFields() == null) return out;
        
        String rootSelector = crawlerCfg.getRootSelector();
        Locator rootLocator = rootSelector != null && !rootSelector.isBlank() 
            ? page.locator(rootSelector).first() 
            : page.locator("body").first();
        
        for (Config.Field f : crawlerCfg.getFields()) {
            try {
                // Execute pre-actions if any
                if (f.getPreActions() != null) {
                    for (Config.PreAction action : f.getPreActions()) {
                        try {
                            page.evaluate(action.getScript());
                            Thread.sleep(500); // Small delay after action
                        } catch (Exception e) {
                            log.warn("Pre-action '{}' failed: {}", action.getId(), e.getMessage());
                        }
                    }
                }

                Object v = extractFromLocator(rootLocator, f);
                out.put(f.getName(), v);
            } catch (Exception e) {
                log.warn("Field '{}' failed: {}", f.getName(), e.toString());
                out.put(f.getName(), null);
            }
        }
        return out;
    }

    private String buildSelector(String rootSelector, String fieldSelector) {
        if (rootSelector == null || rootSelector.isBlank()) {
            return fieldSelector;
        }
        if (fieldSelector.startsWith(">")) {
            return rootSelector + " " + fieldSelector;
        }
        return rootSelector + " " + fieldSelector;
    }

    public Object extractFromLocator(Locator baseLocator, Config.Field field) {
        try {
            // Handle multiple images case first
            if ("image".equals(field.getType()) && field.isMultiple()) {
                return baseLocator.locator(field.getSelector()).all().stream()
                        .map(l -> {
                            String src = l.getAttribute("src");
                            if (src != null && !src.isBlank()) {
                                return downloadImage(baseLocator.page(), src);
                            }
                            return null;
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
            
            var fieldLocator = baseLocator.locator(field.getSelector()).first();
            
            return switch (field.getType()) {
                case "text" -> {
                    String tagName = fieldLocator.evaluate("e => e.tagName").toString().toLowerCase();
                    if ("input".equals(tagName) || "textarea".equals(tagName)) {
                        yield fieldLocator.inputValue();
                    }
                    yield fieldLocator.innerText().trim();
                }
                case "html" -> {
                    String htmlContent;
                    String tagName = fieldLocator.evaluate("e => e.tagName").toString().toLowerCase();
                    if ("textarea".equals(tagName)) {
                        htmlContent = fieldLocator.inputValue();
                    } else {
                        htmlContent = fieldLocator.innerHTML();
                    }
                    
                    // Extract and download images from HTML content
                    htmlContent = extractAndDownloadImagesFromHtml(baseLocator.page(), htmlContent);
                    yield htmlContent;
                }
                case "select" -> {
                    Locator sel = fieldLocator.locator("option:checked");
                    yield sel.count() > 0 ? sel.first().innerText().trim() : "";
                }
                case "checkbox" -> fieldLocator.isChecked();
                case "boolean" -> getBooleanFromLocator(fieldLocator, field.getValues());
                case "image" -> {
                    String src = fieldLocator.getAttribute("src");
                    yield src != null && !src.isBlank() ? downloadImage(baseLocator.page(), src) : null;
                }
                case "label" -> fieldLocator.innerText().trim();
                default -> fieldLocator.innerText().trim();
            };
        } catch (Exception e) {
            log.warn("Failed to extract field '{}': {}", field.getName(), e.getMessage());
            return null;
        }
    }

    private String extractAndDownloadImagesFromHtml(Page page, String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return htmlContent;
        }

        List<String> imageSrcs = extractImageSrcsFromHtml(htmlContent);
        
        for (String imageSrc : imageSrcs) {
            // Skip data URIs and external URLs that don't need downloading
            if (imageSrc.startsWith("data:") || imageSrc.startsWith("http://") || imageSrc.startsWith("https://")) {
                continue;
            }
            
            try {
                String downloadedPath = downloadImage(page, imageSrc);
                if (downloadedPath != null) {
                    log.debug("Downloaded image from HTML: '{}' to '{}'", imageSrc, downloadedPath);
                }
            } catch (Exception e) {
                log.warn("Failed to download image from HTML content: {}", imageSrc, e);
            }
        }
        
        return htmlContent; // Return original HTML content unchanged
    }

    public List<String> extractImageSrcsFromHtml(String htmlContent) {
        List<String> imageSrcs = new java.util.ArrayList<>();
        if (htmlContent == null || htmlContent.isBlank()) {
            return imageSrcs;
        }

        // Pattern to match src attributes in HTML (both quoted and unquoted, with escaped quotes)
        Pattern srcPattern = Pattern.compile("src\\s*=\\s*[\"']?([^\"'\\s>]+)[\"']?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = srcPattern.matcher(htmlContent);
        
        while (matcher.find()) {
            String originalSrc = matcher.group(1);
            // Handle escaped quotes
            String cleanSrc = originalSrc.replace("\\\"", "").replace("\\'", "");
            imageSrcs.add(cleanSrc);
        }
        
        return imageSrcs;
    }

    private boolean getBooleanFromLocator(Locator baseLocator, List<Config.BooleanValue> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }

        for (Config.BooleanValue boolValue : values) {
            if ("Có".equalsIgnoreCase(boolValue.getName()) || "true".equalsIgnoreCase(boolValue.getName())) {
                var check = baseLocator.locator(boolValue.getValue());
                if (check.count() > 0) {
                    return true;
                }
            }
            if ("Không".equalsIgnoreCase(boolValue.getName()) || "false".equalsIgnoreCase(boolValue.getName())) {
                var check = baseLocator.locator(boolValue.getValue());
                if (check.count() > 0) {
                    return false;
                }
            }
        }
        return false;
    }

    private String downloadImage(Page page, String imageUrl) {
        try {
            // Make URL absolute if needed
            URL url = UrlUtils.toAbsolute(page.url(), imageUrl).toURL();
            
            // Use new path format only for /upload URLs
            if (imageUrl.startsWith("/upload")) {
                // For /upload paths, preserve the directory structure
                String relativePath = imageUrl.substring(1); // Remove leading slash
                String cleanPath = UrlUtils.urlDecode(relativePath);
                Path imagePath = rootDir.resolve(cleanPath);
                
                // Create parent directories
                Files.createDirectories(imagePath.getParent());
                
                storeImage(imagePath, url);
                return "images/" + cleanPath.replace("\\", "/");
            } else {
                // Use existing hash-based approach for other URLs
                String fileName = Paths.get(url.getPath()).getFileName().toString().replaceAll("%20", "_");
                var cleanFileName = UrlUtils.urlDecode(fileName);
                log.info("Downloading image: {} with name: {}", url, cleanFileName);
                if (cleanFileName.isEmpty()) {
                    cleanFileName = "image_" + System.currentTimeMillis() + ".jpg";
                }

                // Create subdirectory based on page URL hash
                String pageUrlHash = md5Hash(page.url());
                Path pageImageDir = imageDir.resolve(pageUrlHash);
                Files.createDirectories(pageImageDir);
                
                Path imagePath = pageImageDir.resolve(cleanFileName);

                storeImage(imagePath, url);
                return "images/" + pageUrlHash + "/" + fileName; // Return relative path with hash subdirectory
            }
        } catch (Exception e) {
            log.warn("Failed to download image: {}", imageUrl, e);
            return null; // Return null instead of throwing to allow processing to continue
        }
    }

    private void storeImage(Path imagePath, URL url) throws IOException {
        // Download only if file doesn't exist
        if (!Files.exists(imagePath)) {
            if (config.getParallelism() > 0) {
                executor.submit(() -> {
                    log.info("Downloading image: {} to path: {}", url, imagePath);
                    try (InputStream in = url.openStream()) {
                        Files.copy(in, imagePath, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        log.warn("Failed to download image: {}", url, e);
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } else {
                try (InputStream in = url.openStream()) {
                    Files.copy(in, imagePath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            log.info("Downloaded image: {}", imagePath);
        }
    }

    private String md5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.warn("Failed to generate MD5 hash for: {}", input, e);
            return "unknown";
        }
    }
}