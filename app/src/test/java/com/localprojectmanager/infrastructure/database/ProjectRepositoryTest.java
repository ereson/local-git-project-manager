package com.localprojectmanager.infrastructure.database;

import com.localprojectmanager.domain.project.PathStatus;
import com.localprojectmanager.domain.project.Project;
import com.localprojectmanager.domain.project.PullStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProjectRepositoryTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @TempDir
    Path tempDirectory;

    private ProjectRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        var database = new Database(tempDirectory.resolve("data/app.db"));
        database.initialize();
        repository = new ProjectRepository(database);
    }

    @Test
    void savesFindsUpdatesAndDeletesProjects() throws Exception {
        var path = tempDirectory.resolve("Projects/Demo");
        var project = project(UUID.fromString("1346c1b2-931b-4a85-8bdd-c1648f1dc119"), path);

        repository.save(project);

        assertEquals(project, repository.findById(project.id()).orElseThrow());
        assertEquals(project, repository.findByPath(path.resolve(".")).orElseThrow());
        assertEquals(List.of(project), repository.findAll());

        var renamed = project.rename("Client Demo", CREATED_AT.plusSeconds(60));
        repository.save(renamed);

        assertEquals(renamed, repository.findById(project.id()).orElseThrow());
        assertTrue(repository.delete(project.id()));
        assertFalse(repository.delete(project.id()));
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void rejectsTheSameWindowsPathWithDifferentCase() throws Exception {
        repository.save(project(
                UUID.fromString("85c81379-6aa1-44ce-a850-c8df1c0a2f30"),
                tempDirectory.resolve("Projects/Repository")
        ));

        var duplicate = project(
                UUID.fromString("132f14a8-d17d-428d-a31e-68b86b54e8f4"),
                tempDirectory.resolve("projects/repository")
        );

        assertThrows(SQLException.class, () -> repository.save(duplicate));
    }

    @Test
    void rollsBackTheWholeImportWhenOneProjectFails() throws Exception {
        var id = UUID.fromString("e6ba61ec-e6ca-4337-afd4-e7ce06c9e7d5");
        var first = project(id, tempDirectory.resolve("Projects/First"));
        var conflictingId = project(id, tempDirectory.resolve("Projects/Second"));

        assertThrows(
                SQLException.class,
                () -> repository.insertIgnoringDuplicatePaths(List.of(first, conflictingId))
        );
        assertTrue(repository.findAll().isEmpty());
    }

    private static Project project(UUID id, Path path) {
        return new Project(
                id,
                path.getFileName().toString(),
                path.getFileName().toString(),
                path,
                UUID.fromString("ef14c46b-7d58-46cb-a3a1-733a524c781a"),
                UUID.fromString("bb273b96-cbdd-44c6-867e-c1f7c5a4bd30"),
                PullStrategy.MERGE,
                CREATED_AT.plusSeconds(30),
                PathStatus.NORMAL,
                false,
                null,
                CREATED_AT,
                CREATED_AT
        );
    }
}
