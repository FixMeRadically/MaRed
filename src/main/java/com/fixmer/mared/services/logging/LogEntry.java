package com.fixmer.mared.services.logging;

/**
 * Запись лога.
 *
 * 0.3.1 (structured logging):
 *   level и category вычисляются один раз при ingestion.
 *   Парсинг — только первый тег строки.
 */
public final class LogEntry {

    public final String time;
    public final String text;
    public final long epochMillis;

    public final LogSettings.Level level;
    public final String category;
    public final String message;
    public final String source;

    public LogEntry(String time, String text) {
        this(time, System.currentTimeMillis(), text, null);
    }

    public LogEntry(String time, long epochMillis, String text, String source) {
        this.time = time != null ? time : "";
        this.text = text != null ? text : "";
        this.epochMillis = epochMillis;
        this.level = LogSettings.parseLevel(this.text);
        this.category = LogSettings.parseCategory(this.text);
        this.message = stripLeadingTag(this.text);
        this.source = source;
    }

    public LogEntry(String time, long epochMillis,
                    LogSettings.Level level, String category,
                    String message, String source) {
        this.time = time != null ? time : "";
        this.epochMillis = epochMillis;
        this.level = level != null ? level : LogSettings.Level.INFO;
        this.category = category != null ? category : "other";
        this.message = message != null ? message : "";
        this.source = source;
        this.text = "[" + this.category + "] " + this.message;
    }

    private static String stripLeadingTag(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.charAt(0) != '[') return s;
        int close = s.indexOf(']');
        if (close <= 0 || close > 20) return s;
        int from = close + 1;
        while (from < s.length() && s.charAt(from) == ' ') from++;
        return s.substring(from);
    }

    @Override
    public String toString() {
        return "[" + time + "] " + text;
    }
}