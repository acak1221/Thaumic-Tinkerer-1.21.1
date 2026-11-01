package thaumic.tinkerer.mpserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the dedicated server of the multiplayer check saw, written to {@code server-report.json} after every change. Test-only
 * code, new in the 1.21.1 port (no 1.7.10 counterpart); CC BY-NC-SA 3.0 like the add-on (see NOTICE.md), never part of a release
 * jar.
 *
 * <p>A check is {@code PASS} or {@code FAIL} (what the add-on did on the server), {@code HARNESS_ERROR} (the harness itself threw)
 * or {@code INFO}. The harness status is {@code RUNNING}, then {@code COMPLETED} when the server was stopped as asked,
 * {@code TIMED_OUT} (the watchdog) or {@code ABORTED} (the JVM ended otherwise).</p>
 */
final class ServerReport {
    enum Result { PASS, FAIL, HARNESS_ERROR, INFO }

    record Check(String scenario, String name, Result result, String detail, String at) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final Map<String, Object> harness = new LinkedHashMap<>();
    private final Map<String, Object> facts = new LinkedHashMap<>();
    private final List<Check> checks = new ArrayList<>();
    private final List<Map<String, Object>> events = new ArrayList<>();

    ServerReport(Path file, String loader) {
        this.file = file;
        harness.put("status", "RUNNING");
        harness.put("loader", loader);
        harness.put("started_utc", Instant.now().toString());
    }

    Path file() {
        return file;
    }

    synchronized void add(String scenario, String name, Result result, String detail) {
        checks.add(new Check(scenario, name, result, detail == null ? "" : detail, Instant.now().toString()));
        write();
    }

    void check(String scenario, String name, boolean ok, String detail) {
        add(scenario, name, ok ? Result.PASS : Result.FAIL, detail);
    }

    void info(String scenario, String name, String detail) {
        add(scenario, name, Result.INFO, detail);
    }

    synchronized void fact(String key, Object value) {
        facts.put(key, value);
        write();
    }

    /** A join, a leave, a command: the log of what happened, in order. */
    synchronized void event(String kind, String player, String detail) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("at", Instant.now().toString());
        entry.put("kind", kind);
        entry.put("player", player);
        entry.put("detail", detail);
        events.add(entry);
        write();
    }

    synchronized boolean has(String scenario) {
        return checks.stream().anyMatch(c -> c.scenario().equals(scenario) && c.result() != Result.INFO);
    }

    synchronized List<Check> checks() {
        return new ArrayList<>(checks);
    }

    synchronized String status() {
        return String.valueOf(harness.get("status"));
    }

    synchronized void status(String status, String detail) {
        harness.put("status", status);
        if (detail != null) harness.put("detail", detail);
        harness.put("finished_utc", Instant.now().toString());
        write();
    }

    synchronized void write() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("harness", harness);
        Map<String, Object> summary = new LinkedHashMap<>();
        for (Result result : Result.values()) summary.put(result.name(), checks.stream().filter(c -> c.result() == result).count());
        root.put("summary", summary);
        root.put("facts", facts);
        root.put("checks", checks);
        root.put("events", events);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write the multiplayer server report " + file, e);
        }
    }
}
