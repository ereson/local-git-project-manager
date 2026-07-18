package com.localprojectmanager.domain.scan;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class DefaultIgnoreRules {

    private static final Set<String> DIRECTORY_NAMES = Set.of(
            "node_modules",
            "target",
            "build",
            "dist",
            "out",
            "vendor",
            ".idea",
            ".vscode",
            ".gradle",
            ".next",
            "coverage"
    );

    private DefaultIgnoreRules() {
    }

    public static boolean shouldIgnore(Path directory) {
        var name = Objects.requireNonNull(directory).getFileName();
        return name != null && DIRECTORY_NAMES.contains(name.toString().toLowerCase(Locale.ROOT));
    }
}
