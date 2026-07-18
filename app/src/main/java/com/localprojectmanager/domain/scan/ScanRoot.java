package com.localprojectmanager.domain.scan;

import com.localprojectmanager.domain.path.WindowsPathNormalizer;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ScanRoot(
        UUID id,
        Path path,
        boolean enabled,
        Instant lastScanAt,
        String lastScanStatus,
        String lastScanError,
        Instant createdAt
) {

    public ScanRoot {
        Objects.requireNonNull(id, "id");
        path = WindowsPathNormalizer.normalize(path);
        Objects.requireNonNull(createdAt, "createdAt");
    }
}

