package com.localprojectmanager.infrastructure.git;

import java.util.Objects;

public record GitVersion(String value) {

    public GitVersion {
        if (Objects.requireNonNull(value).isBlank()) {
            throw new IllegalArgumentException("Git version must not be blank");
        }
    }
}
