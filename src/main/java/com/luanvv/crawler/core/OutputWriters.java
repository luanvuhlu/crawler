package com.luanvv.crawler.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.opencsv.CSVWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OutputWriters {
    private final Path baseDir;
    private final boolean jsonEnabled;
    private final boolean csvEnabled;
    private final ObjectMapper objectMapper;

    public OutputWriters(Config.Output cfg) throws IOException {
        this.baseDir = Path.of(cfg.getDir());
        Files.createDirectories(baseDir);
        this.jsonEnabled = cfg.isJson();
        this.csvEnabled = cfg.isCsv();
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void writeForUrl(String url, Map<String, Object> record) {
        String safe = UrlUtils.sanitizeForFilename(url);
        if (jsonEnabled) writeJson(baseDir.resolve(safe + ".json"), record);
        if (csvEnabled) writeCsv(baseDir.resolve(safe + ".csv"), record);
    }

    private void writeJson(Path path, Map<String, Object> record) {
        try {
            objectMapper.writeValue(path.toFile(), record);
            log.debug("Wrote JSON {}", path);
        } catch (IOException e) {
            log.error("Failed to write JSON {}", path, e);
        }
    }

    private void writeCsv(Path path, Map<String, Object> record) {
        try (Writer w = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
             CSVWriter csv = new CSVWriter(w)) {
            List<String> headers = record.keySet().stream().toList();
            String[] headerRow = headers.toArray(String[]::new);
            String[] values = headers.stream().map(k -> toStringSafe(record.get(k))).toArray(String[]::new);
            csv.writeNext(headerRow);
            csv.writeNext(values);
            log.debug("Wrote CSV {}", path);
        } catch (IOException e) {
            log.error("Failed to write CSV {}", path, e);
        }
    }

    private String toStringSafe(Object v) {
        if (v == null) return "";
        if (v instanceof List<?> list) return String.join("|", list.stream().map(String::valueOf).toList());
        if (v instanceof Map<?, ?> map) return map.toString();
        return String.valueOf(v);
    }
}
