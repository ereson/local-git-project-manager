package com.localprojectmanager.domain.git;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record GitOperationRecord(
        UUID projectId,
        Type type,
        Instant startedAt,
        Instant finishedAt,
        Status status,
        String summary,
        String rawError
) {

    public GitOperationRecord {
        Objects.requireNonNull(projectId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(finishedAt);
        Objects.requireNonNull(status);
    }

    public enum Type {
        FETCH,
        PULL,
        SWITCH_BRANCH
    }

    public enum Status {
        SUCCESS,
        FAILED,
        CONFLICT
    }
}
