package jp.hakamap.project.application.history;

import java.time.Instant;

public record HistoryEntry(
    CommandId commandId,
    CommandType commandType,
    Instant commandTimestamp,
    int targetCount,
    boolean applied,
    boolean savedMarker) {}
