package com.localprojectmanager.domain.ide;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

public record IdeConfig(
        UUID id,
        String name,
        String version,
        Path executablePath,
        boolean available
) {

    public IdeConfig {
        Objects.requireNonNull(id, "id");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        name = name.strip();
        version = version == null || version.isBlank() ? null : version.strip();
        executablePath = Objects.requireNonNull(executablePath)
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public String toString() {
        return version == null ? name : name + " " + version;
    }
}
