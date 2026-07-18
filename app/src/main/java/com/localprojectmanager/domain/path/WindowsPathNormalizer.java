package com.localprojectmanager.domain.path;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public final class WindowsPathNormalizer {

    private WindowsPathNormalizer() {
    }

    public static Path normalize(Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    public static String comparisonKey(Path path) {
        return normalize(path)
                .toString()
                .replace('\\', '/')
                .toLowerCase(Locale.ROOT);
    }
}

