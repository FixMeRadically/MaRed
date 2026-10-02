package com.fixmer.mared.services.logging;

/**
 * 0.3.0 (Phase C): вынесен из MaredLogPanel.LogEntry в top-level.
 * 0.3.0 (Phase F3): переехал в services/logging (не UI).
 */
public final class LogEntry {

    public final String time;
    public final String text;

    public LogEntry(String time, String text) {
        this.time = time;
        this.text = text;
    }
}