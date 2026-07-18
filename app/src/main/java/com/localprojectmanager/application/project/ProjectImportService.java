package com.localprojectmanager.application.project;

import com.localprojectmanager.application.scan.GitRepositoryScanner.DiscoveredRepository;
import com.localprojectmanager.domain.path.WindowsPathNormalizer;
import com.localprojectmanager.domain.project.PathStatus;
import com.localprojectmanager.domain.project.Project;
import com.localprojectmanager.domain.project.PullStrategy;
import com.localprojectmanager.domain.scan.ScanRoot;
import com.localprojectmanager.infrastructure.database.ProjectRepository;
import com.localprojectmanager.infrastructure.database.ScanRootRepository;

import java.sql.SQLException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.util.function.Supplier;

public final class ProjectImportService {

    private final ProjectRepository projects;
    private final ScanRootRepository scanRoots;
    private final Supplier<PullStrategy> defaultPullStrategy;

    public ProjectImportService(ProjectRepository projects, ScanRootRepository scanRoots) {
        this(projects, scanRoots, () -> PullStrategy.REBASE);
    }

    public ProjectImportService(
            ProjectRepository projects,
            ScanRootRepository scanRoots,
            Supplier<PullStrategy> defaultPullStrategy
    ) {
        this.projects = Objects.requireNonNull(projects);
        this.scanRoots = Objects.requireNonNull(scanRoots);
        this.defaultPullStrategy = Objects.requireNonNull(defaultPullStrategy);
    }

    public ImportResult importRepositories(List<DiscoveredRepository> repositories)
            throws SQLException {
        var requested = List.copyOf(Objects.requireNonNull(repositories));
        var roots = scanRoots.findAll();
        var now = Instant.now();
        var imported = projects.insertIgnoringDuplicatePaths(
                requested.stream().map(repository -> toProject(
                        repository, roots, now, defaultPullStrategy.get()
                )).toList()
        );
        return new ImportResult(imported, requested.size() - imported);
    }

    public DiscoveredRepository inspectManualProject(Path directory) {
        return com.localprojectmanager.application.scan.GitRepositoryScanner.inspect(directory);
    }

    public List<DiscoveredRepository> findAlreadyImported(
            List<DiscoveredRepository> repositories
    ) throws SQLException {
        var existingPaths = projects.findAll().stream()
                .map(project -> WindowsPathNormalizer.comparisonKey(project.path()))
                .collect(Collectors.toSet());
        return repositories.stream()
                .filter(repository -> existingPaths.contains(
                        WindowsPathNormalizer.comparisonKey(repository.path())
                ))
                .toList();
    }

    public List<Project> listProjects() throws SQLException {
        var current = projects.findAll().stream().map(this::refreshPathStatus).toList();
        return current.stream()
                .sorted(Comparator.comparing(
                        Project::lastOpenedAt,
                        Comparator.nullsLast(Comparator.<Instant>reverseOrder())
                ).thenComparing(Project::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Project rename(Project project, String displayName) throws SQLException {
        var updated = Objects.requireNonNull(project).rename(displayName, Instant.now());
        projects.save(updated);
        return updated;
    }

    public Project relocate(Project project, Path newPath) throws SQLException {
        var repository = inspectManualProject(newPath);
        var existing = projects.findByPath(repository.path());
        if (existing.isPresent() && !existing.get().id().equals(project.id())) {
            throw new IllegalArgumentException("Project path already exists");
        }
        var updated = Objects.requireNonNull(project).relocated(repository.path(), Instant.now());
        projects.save(updated);
        return updated;
    }

    public boolean remove(Project project) throws SQLException {
        return projects.delete(Objects.requireNonNull(project).id());
    }

    private Project refreshPathStatus(Project project) {
        var status = !Files.isDirectory(project.path())
                ? PathStatus.UNAVAILABLE
                : !Files.isReadable(project.path())
                        ? PathStatus.INACCESSIBLE
                        : PathStatus.NORMAL;
        if (status == project.pathStatus()) {
            return project;
        }
        var updated = project.withPathStatus(status, Instant.now());
        try {
            projects.save(updated);
            return updated;
        } catch (SQLException exception) {
            return project;
        }
    }

    public List<Project> searchProjects(String query) throws SQLException {
        var needle = Objects.requireNonNull(query).strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return listProjects();
        }
        return listProjects().stream()
                .filter(project -> contains(project.displayName(), needle)
                        || contains(project.directoryName(), needle)
                        || contains(project.path().toString(), needle))
                .toList();
    }

    public Project setPullStrategy(Project project, PullStrategy strategy) throws SQLException {
        var updated = Objects.requireNonNull(project).withPullStrategy(strategy, Instant.now());
        projects.save(updated);
        return updated;
    }

    private static boolean contains(String value, String needle) {
        return value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static Project toProject(
            DiscoveredRepository repository,
            List<ScanRoot> scanRoots,
            Instant now,
            PullStrategy pullStrategy
    ) {
        var path = repository.path();
        var fileName = path.getFileName();
        var directoryName = fileName == null ? path.toString() : fileName.toString();
        var scanRootId = scanRoots.stream()
                .filter(scanRoot -> path.startsWith(scanRoot.path()))
                .max(Comparator.comparingInt(scanRoot -> scanRoot.path().getNameCount()))
                .map(ScanRoot::id)
                .orElse(null);
        return new Project(
                UUID.randomUUID(),
                directoryName,
                directoryName,
                path,
                scanRootId,
                null,
                pullStrategy,
                null,
                PathStatus.NORMAL,
                repository.nestedRepository(),
                repository.parentRepositoryPath(),
                now,
                now
        );
    }

    public record ImportResult(int importedCount, int duplicateCount) {
    }
}
