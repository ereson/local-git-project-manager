package com.localprojectmanager.infrastructure.git;

import java.util.Objects;

public record GitHead(String value, boolean detached) {

    public GitHead {
        if (Objects.requireNonNull(value).isBlank()) {
            throw new IllegalArgumentException("Git head must not be blank");
        }
    }

    public String displayName() {
        return detached ? "Detached HEAD · " + value : value;
    }
}
