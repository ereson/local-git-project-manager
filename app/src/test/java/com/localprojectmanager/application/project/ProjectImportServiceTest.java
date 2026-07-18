package com.localprojectmanager.application.project;

import com.localprojectmanager.application.scan.GitRepositoryScanner.DiscoveredRepository;
import com.localprojectmanager.domain.project.Project;
import com.localprojectmanager.domain.scan.ScanRoot;
import com.localprojectmanager.infrastructure.database.Database;
import com.localprojectmanager.infrastructure.database.ProjectRepository;
import com.localprojectmanager.infrastructure.database.ScanRootRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class ProjectImportServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void importsProjectsAtomicallyAssociatesRootsAndSkipsDuplicatePaths() throws Exception {
        var database = new Database(tempDirectory.resolve("data/app.db"));
        database.initialize();
        var projectRepository = new ProjectRepository(database);
        var scanRootRepository = new ScanRootRepository(database);
        var mainRoot = scanRoot(tempDirectory.resolve("Projects"));
        var childRoot = scanRoot(tempDirectory.resolve("Projects/parent/child"));
        scanRootRepository.save(mainRoot);
        scanRootRepository.save(childRoot);
        var service = new ProjectImportService(projectRepository, scanRootRepository);
        var parent = new DiscoveredRepository(tempDirectory.resolve("Projects/parent"), null);
        var child = new DiscoveredRepository(
                tempDirectory.resolve("Projects/parent/child"),
                parent.path()
        );

        var first = service.importRepositories(List.of(parent, child));

        assertEquals(2, first.importedCount());
        assertEquals(mainRoot.id(), projectRepository.findByPath(parent.path()).orElseThrow().scanRootId());
        var importedChild = projectRepository.findByPath(child.path()).orElseThrow();
        assertEquals(childRoot.id(), importedChild.scanRootId());
        assertTrue(importedChild.nestedRepository());
        assertEquals(parent.path().toAbsolutePath(), importedChild.parentRepositoryPath());
        assertEquals(List.of(parent, child), service.findAlreadyImported(List.of(parent, child)));

        var importedParent = projectRepository.findByPath(parent.path()).orElseThrow();
        var openedParent = withLastOpened(importedParent, importedParent.updatedAt().plusSeconds(1))
                .rename("Enterprise Admin", importedParent.updatedAt().plusSeconds(1));
        projectRepository.save(openedParent);
        assertEquals(
                List.of(openedParent.id(), importedChild.id()),
                service.listProjects().stream().map(Project::id).toList()
        );
        assertEquals(List.of(openedParent.id()), ids(service.searchProjects("ENTERPRISE")));
        assertEquals(List.of(importedChild.id()), ids(service.searchProjects("child")));
        assertEquals(
                List.of(openedParent.id(), importedChild.id()),
                ids(service.searchProjects(
                        tempDirectory.getFileName().toString().toUpperCase(Locale.ROOT)
                ))
        );

        var second = service.importRepositories(List.of(parent, child));

        assertEquals(0, second.importedCount());
        assertEquals(2, second.duplicateCount());
        assertEquals(2, projectRepository.findAll().size());
    }

    @Test
    void preservesMissingProjectAndAllowsRelocationRenameAndRemoval() throws Exception {
        var database = new Database(tempDirectory.resolve("lifecycle/app.db"));
        database.initialize();
        var repository = new ProjectRepository(database);
        var service = new ProjectImportService(repository, new ScanRootRepository(database));
        var oldPath = Files.createDirectories(tempDirectory.resolve("old"));
        Files.createDirectory(oldPath.resolve(".git"));
        service.importRepositories(List.of(new DiscoveredRepository(oldPath, null)));
        var project = service.listProjects().getFirst();

        Files.delete(oldPath.resolve(".git"));
        Files.delete(oldPath);
        project = service.listProjects().getFirst();
        assertEquals(com.localprojectmanager.domain.project.PathStatus.UNAVAILABLE, project.pathStatus());

        var newPath = Files.createDirectories(tempDirectory.resolve("new project"));
        Files.createDirectory(newPath.resolve(".git"));
        project = service.relocate(project, newPath);
        project = service.rename(project, "新名称");
        assertEquals(newPath.toAbsolutePath().normalize(), project.path());
        assertEquals("新名称", project.displayName());
        assertTrue(service.remove(project));
        assertFalse(repository.findById(project.id()).isPresent());
    }

    private static ScanRoot scanRoot(Path path) {
        return new ScanRoot(
                UUID.randomUUID(),
                path,
                true,
                null,
                null,
                null,
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private static Project withLastOpened(Project project, Instant openedAt) {
        return new Project(
                project.id(),
                project.displayName(),
                project.directoryName(),
                project.path(),
                project.scanRootId(),
                project.defaultIdeId(),
                project.pullStrategy(),
                openedAt,
                project.pathStatus(),
                project.nestedRepository(),
                project.parentRepositoryPath(),
                project.createdAt(),
                openedAt
        );
    }

    private static List<UUID> ids(List<Project> projects) {
        return projects.stream().map(Project::id).toList();
    }
}
