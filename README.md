# GachMenGiaTot Crawler

Java Playwright crawler skeleton with login, session management, rate limiting (Bucket4j), retries, logging, and JSON/CSV persistence.

## Prereqs
- Java 17+
- Maven 3.9+
- Browsers installed by Playwright (first run downloads automatically)

## Configure
Edit `src/main/resources/crawler-config.yaml`.
Set credentials via environment variables named in config (e.g., `CRAWLER_USERNAME`, `CRAWLER_PASSWORD`).

## Run
```
mvn -q -DskipTests package
java -jar target/gachmengiatot-crawler-0.1.0-SNAPSHOT.jar
```

Or run tests:
```
mvn -q test
```

## Notes
- Headless is enabled by default; set `headless: false` for debugging.
- Outputs are written to `data/` with filenames derived from the URL (both JSON and CSV).
- Adjust rate limiting and retries in config to be polite and resilient.
- The extractor knows about list/detail pages and common field types including CKEditor HTML.
