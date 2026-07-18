package com.localprojectmanager.domain.scan;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DefaultIgnoreRulesTest {

    @Test
    void ignoresEveryBuiltInDirectoryNameWithoutCaseSensitivity() {
        var names = new String[]{
                "node_modules", "target", "build", "dist", "out", "vendor",
                ".idea", ".vscode", ".gradle", ".next", "coverage"
        };

        for (var name : names) {
            assertTrue(DefaultIgnoreRules.shouldIgnore(Path.of("Projects", name.toUpperCase())));
        }
    }

    @Test
    void doesNotIgnorePartialMatchesOrFilesystemRoots() {
        assertFalse(DefaultIgnoreRules.shouldIgnore(Path.of("Projects", "builder")));
        assertFalse(DefaultIgnoreRules.shouldIgnore(Path.of("C:\\")));
    }
}
