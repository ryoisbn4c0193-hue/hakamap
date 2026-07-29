package jp.hakamap.infrastructure.lifecycle;

import java.time.Instant;
import java.util.UUID;

public record UncleanExitMarker(
    UUID instanceId, String applicationVersion, Instant startedAt, Instant heartbeatAt) {}
