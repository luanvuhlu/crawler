package com.luanvv.crawler.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;

public class UrlUtils {

  public static final Map<String, String> REPLACEMENTS = Map.of(
      " ", "%20",
      "\\[", "%5B",
      "]", "%5D"
  );

  public static URI toAbsolute(String baseUrl, String href) {
    if (href == null || href.isBlank()) {
      return null;
    }
    try {
      URI base = new URI(baseUrl);
      return base.resolve(urlEncode(href));
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid URL: " + href, e);
    }
  }

  public static String urlEncode(String s) {
    for (Map.Entry<String, String> e : REPLACEMENTS.entrySet()) {
      s = s.replaceAll(e.getKey(), e.getValue());
    }
    return s;
  }

  public static String urlDecode(String s) {
    for (Map.Entry<String, String> e : REPLACEMENTS.entrySet()) {
      s = s.replaceAll(e.getValue(), e.getKey());
    }
    return s;
  }

  public static String sanitizeForFilename(String url) {
    String safe = url
        .replaceFirst("^[a-zA-Z]+://", "")
        .replaceAll("[^a-zA-Z0-9._-]", "_");
    if (safe.length() > 120) {
      // ensure deterministic short name by hashing
      String hash = sha1(url).substring(0, 10);
      safe = safe.substring(0, 100) + "_" + hash;
    }
    return safe.toLowerCase(Locale.ROOT);
  }

  private static String sha1(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] b = md.digest(s.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte value : b) {
        sb.append(String.format("%02x", value));
      }
      return sb.toString();
    } catch (Exception e) {
      return Integer.toHexString(s.hashCode());
    }
  }
}
