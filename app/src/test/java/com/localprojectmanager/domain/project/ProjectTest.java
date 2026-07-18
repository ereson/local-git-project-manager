package com.localprojectmanager.domain.project;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ProjectTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void renamesWithoutChangingTheDirectoryOrPath() {
        var sourcePath = Path.of("projects", ".", "demo");
        var project = project(sourcePath, false, null);

        var renamed = project.rename("  Client project  ", CREATED_AT.plusSeconds(1));
        var reset = renamed.resetDisplayName(CREATED_AT.plusSeconds(2));

        assertEquals("Client project", renamed.displayName());
        assertEquals("demo", renamed.directoryName());
        assertEquals(sourcePath.toAbsolutePath().normalize(), renamed.path());
        assertEquals("demo", reset.displayName());
        assertThrows(
                IllegalArgumentException.class,
                () -> project.rename(" ", CREATED_AT.plusSeconds(1))
        );
    }

    @Test
    void requiresParentPathOnlyForNestedRepositories() {
        var parent = Path.of("projects", "parent");
        var child = parent.resolve("child");

        assertEquals(
                parent.toAbsolutePath().normalize(),
                project(child, true, parent).parentRepositoryPath()
        );
        assertThrows(IllegalArgumentException.class, () -> project(child, true, null));
        assertThrows(IllegalArgumentException.class, () -> project(child, false, parent));
    }

    private static Project project(Path path, boolean nested, Path parent) {
        return new Project(
                UUID.fromString("79f31db4-d11a-4051-a28d-fb12f03d62d7"),
                "demo",
                "demo",
                path,
                null,
                null,
                null,
                null,
                PathStatus.NORMAL,
                nested,
                parent,
                CREATED_AT,
                CREATED_AT
        );
    }
}

