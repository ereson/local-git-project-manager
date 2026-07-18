package com.localprojectmanager.infrastructure.ide;

import java.nio.file.Path;
import java.util.Objects;

public record DetectedIde(String name, String version, Path executablePath) {

    public DetectedIde {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        name = name.strip();
        version = version == null || version.isBlank() ? null : version.strip();
        executablePath = Objects.requireNonNull(executablePath)
                .toAbsolutePath()
                .normalize();
    }
}
