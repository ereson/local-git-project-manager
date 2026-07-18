package com.localprojectmanager.infrastructure.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DatabaseTest {

    @TempDir
    Path tempDirectory;

    @Test
    void initializesAndMigratesDatabaseOnlyOnce() throws Exception {
        var file = tempDirectory.resolve("nested/app.db");
        var database = new Database(file);

        database.initialize();
        database.initialize();

        assertTrue(Files.isRegularFile(file));
        try (var connection = database.openConnection();
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("PRAGMA foreign_keys")) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM schema_version")) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
            try (var result = statement.executeQuery("""
                    SELECT COUNT(*) FROM sqlite_master
                    WHERE type = 'table' AND name = 'projects'
                    """)) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
        }
    }

    @Test
    void portableRuntimeKeepsDataInsideApplicationDirectory() throws Exception {
        var portable = tempDirectory.resolve("portable");
        System.setProperty("lpm.portable.dir", portable.toString());
        try {
            Database.forRuntime(true).initialize();
            assertTrue(Files.isRegularFile(portable.resolve("data/app.db")));
        } finally {
            System.clearProperty("lpm.portable.dir");
        }
    }

    @Test
    void reopeningDatabasePreservesProjectsIdesScanRootsAndSettings() throws Exception {
        var file = tempDirectory.resolve("upgrade/app.db");
        var database = new Database(file);
        database.initialize();
        var now = java.time.Instant.parse("2026-07-18T12:00:00Z");
        var root = new com.localprojectmanager.domain.scan.ScanRoot(
                java.util.UUID.randomUUID(), tempDirectory.resolve("projects"), true,
                now, "SUCCESS", null, now
        );
        new ScanRootRepository(database).save(root);
        var executable = Files.writeString(tempDirectory.resolve("CustomIde.exe"), "test");
        var ide = new IdeConfigRepository(database).saveManual("Custom IDE", executable);
        var projectPath = Files.createDirectories(tempDirectory.resolve("projects/demo"));
        var project = new com.localprojectmanager.domain.project.Project(
                java.util.UUID.randomUUID(), "自定义名称", "demo", projectPath,
                root.id(), ide.id(), com.localprojectmanager.domain.project.PullStrategy.MERGE,
                now, com.localprojectmanager.domain.project.PathStatus.NORMAL,
                false, null, now, now
        );
        new ProjectRepository(database).save(project);
        new ApplicationSettingsRepository(database).put("theme", "DARK");

        var reopened = new Database(file);
        reopened.initialize();

        assertEquals("自定义名称", new ProjectRepository(reopened)
                .findById(project.id()).orElseThrow().displayName());
        assertEquals(ide.id(), new IdeConfigRepository(reopened).findAvailable().getFirst().id());
        assertEquals(root.id(), new ScanRootRepository(reopened).findAll().getFirst().id());
        assertEquals("DARK", new ApplicationSettingsRepository(reopened)
                .get("theme").orElseThrow());
    }
}
