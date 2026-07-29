package jp.hakamap.infrastructure.lifecycle;

import java.time.Instant;
import java.util.UUID;

public record RuntimeInstance(
    long processId, UUID instanceId, int port, Instant startedAt, String controlToken) {}
