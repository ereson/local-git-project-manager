package com.localprojectmanager.application.scan;

import com.localprojectmanager.infrastructure.database.Database;
import com.localprojectmanager.infrastructure.database.ScanRootRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScanRootServiceTest {

    @TempDir
    Path tempDirectory;

    private ScanRootService service;

    @BeforeEach
    void setUp() throws Exception {
        var database = new Database(tempDirectory.resolve("data/app.db"));
        database.initialize();
        service = new ScanRootService(new ScanRootRepository(database));
    }

    @Test
    void addsPersistsWarnsAboutOverlapAndRemovesDirectories() throws Exception {
        var parent = Files.createDirectories(tempDirectory.resolve("Projects"));
        var child = Files.createDirectories(parent.resolve("Child"));

        var first = service.add(parent);
        var second = service.add(child);

        assertFalse(first.overlapsExisting());
        assertTrue(second.overlapsExisting());
        assertEquals(2, service.list().size());
        assertThrows(IllegalArgumentException.class, () -> service.add(parent.resolve(".")));
        assertTrue(service.remove(first.scanRoot().id()));
        assertEquals(1, service.list().size());
    }

    @Test
    void rejectsDirectoriesThatDoNotExist() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.add(tempDirectory.resolve("missing"))
        );
    }
}

