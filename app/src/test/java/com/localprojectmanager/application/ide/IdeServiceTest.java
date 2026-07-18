package com.localprojectmanager.application.ide;

import com.localprojectmanager.domain.project.PathStatus;
import com.localprojectmanager.domain.project.Project;
import com.localprojectmanager.infrastructure.database.Database;
import com.localprojectmanager.infrastructure.database.IdeConfigRepository;
import com.localprojectmanager.infrastructure.database.ProjectRepository;
import com.localprojectmanager.infrastructure.ide.DetectedIde;
import com.localprojectmanager.infrastructure.ide.IdeDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class IdeServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void synchronizesDetectedIdesAndSetsAndClearsTheProjectDefault() throws Exception {
        var database = new Database(tempDirectory.resolve("data/app.db"));
        database.initialize();
        var projects = new ProjectRepository(database);
        var project = project();
        projects.save(project);
        var detected = new AtomicReference<>(List.of(new DetectedIde(
                "Visual Studio Code",
                null,
                tempDirectory.resolve("Code.exe")
        )));
        IdeDetector detector = detected::get;
        var failLaunch = new AtomicBoolean();
        var launchedWith = new AtomicReference<List<Path>>();
        var service = new IdeService(
                new IdeConfigRepository(database),
                projects,
                List.of(detector),
                (executable, projectPath) -> {
                    if (failLaunch.get()) {
                        throw new IOException("launch failed");
                    }
                    launchedWith.set(List.of(executable, projectPath));
                }
        );

        service.refreshDetectedIdes();
        var ide = service.listAvailableIdes().getFirst();
        var configured = service.setDefaultIde(project, ide.id());

        assertEquals(ide.id(), projects.findById(project.id()).orElseThrow().defaultIdeId());
        failLaunch.set(true);
        assertThrows(IOException.class, () -> service.openProject(configured, ide.id()));
        assertNull(projects.findById(project.id()).orElseThrow().lastOpenedAt());

        failLaunch.set(false);
        var opened = service.openProject(configured, ide.id());
        assertEquals(List.of(ide.executablePath(), project.path()), launchedWith.get());
        assertNotNull(opened.lastOpenedAt());
        assertEquals(opened.lastOpenedAt(), projects.findById(project.id()).orElseThrow().lastOpenedAt());

        assertNull(service.clearDefaultIde(opened).defaultIdeId());
        assertNull(projects.findById(project.id()).orElseThrow().defaultIdeId());

        detected.set(List.of());
        service.refreshDetectedIdes();
        assertTrue(service.listAvailableIdes().isEmpty());
    }

    @Test
    void savesManualIdeAndCanOpenWithoutChangingDefault() throws Exception {
        var database = new Database(tempDirectory.resolve("manual/app.db"));
        database.initialize();
        var projects = new ProjectRepository(database);
        var project = project();
        projects.save(project);
        var executable = Files.writeString(tempDirectory.resolve("Custom IDE.exe"), "test");
        var launchedWith = new AtomicReference<List<Path>>();
        var service = new IdeService(
                new IdeConfigRepository(database), projects, List.of(),
                (ide, directory) -> launchedWith.set(List.of(ide, directory))
        );

        var ide = service.addManualIde("Custom IDE", executable);
        var opened = service.openProject(project, ide.id(), false);

        assertEquals(ide, service.listAvailableIdes().getFirst());
        assertEquals(List.of(executable.toAbsolutePath().normalize(), project.path()), launchedWith.get());
        assertNull(opened.defaultIdeId());
        assertNull(projects.findById(project.id()).orElseThrow().defaultIdeId());
        assertNotNull(opened.lastOpenedAt());
    }

    private Project project() throws Exception {
        var createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Files.createDirectories(tempDirectory.resolve("demo"));
        return new Project(
                UUID.randomUUID(),
                "demo",
                "demo",
                tempDirectory.resolve("demo"),
                null,
                null,
                null,
                null,
                PathStatus.NORMAL,
                false,
                null,
                createdAt,
                createdAt
        );
    }
}
