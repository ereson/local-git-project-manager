package com.localprojectmanager.infrastructure.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class ApplicationSettingsRepositoryTest {

    @TempDir
    Path tempDirectory;

    @Test
    void readsDefaultsOverwritesAndPersistsAfterReopen() throws Exception {
        var file = tempDirectory.resolve("app.db");
        var database = new Database(file);
        database.initialize();
        var settings = new ApplicationSettingsRepository(database);

        assertEquals("system", settings.get("theme", "system"));
        assertFalse(settings.getBoolean("updates", false));
        assertEquals(Theme.SYSTEM, settings.getEnum("theme-mode", Theme.class, Theme.SYSTEM));

        settings.put("theme", "dark");
        settings.put("theme", "light");
        settings.put("updates", "true");
        settings.put("theme-mode", "DARK");

        var reopened = new Database(file);
        reopened.initialize();
        var persisted = new ApplicationSettingsRepository(reopened);

        assertEquals("light", persisted.get("theme").orElseThrow());
        assertEquals(true, persisted.getBoolean("updates", false));
        assertEquals(Theme.DARK, persisted.getEnum("theme-mode", Theme.class, Theme.SYSTEM));
    }

    private enum Theme {
        SYSTEM,
        DARK
    }
}
