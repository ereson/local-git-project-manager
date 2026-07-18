package com.localprojectmanager.infrastructure.git;

import java.time.Instant;
import java.util.Objects;

public record GitCommit(String hash, String message, Instant committedAt) {

    public GitCommit {
        if (Objects.requireNonNull(hash).isBlank()) {
            throw new IllegalArgumentException("Commit hash must not be blank");
        }
        Objects.requireNonNull(message);
        Objects.requireNonNull(committedAt);
    }
}
