package com.localprojectmanager.application.ide;

import com.localprojectmanager.domain.ide.IdeConfig;
import com.localprojectmanager.domain.project.PathStatus;
import com.localprojectmanager.domain.project.Project;
import com.localprojectmanager.infrastructure.database.IdeConfigRepository;
import com.localprojectmanager.infrastructure.database.ProjectRepository;
import com.localprojectmanager.infrastructure.ide.IdeDetector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class IdeService {

    private final IdeConfigRepository ides;
    private final ProjectRepository projects;
    private final List<IdeDetector> detectors;
    private final ProcessLauncher launcher;

    public IdeService(
            IdeConfigRepository ides,
            ProjectRepository projects,
            List<IdeDetector> detectors
    ) {
        this(
                ides,
                projects,
                detectors,
                (executable, projectPath) -> new ProcessBuilder(
                        executable.toString(),
                        projectPath.toString()
                ).start()
        );
    }

    IdeService(
            IdeConfigRepository ides,
            ProjectRepository projects,
            List<IdeDetector> detectors,
            ProcessLauncher launcher
    ) {
        this.ides = Objects.requireNonNull(ides);
        this.projects = Objects.requireNonNull(projects);
        this.detectors = List.copyOf(detectors);
        this.launcher = Objects.requireNonNull(launcher);
    }

    public void refreshDetectedIdes() throws SQLException {
        ides.synchronizeDetected(detectors.stream().flatMap(detector -> detector.detect().stream()).toList());
    }

    public List<IdeConfig> listAvailableIdes() throws SQLException {
        return ides.findAvailable();
    }

    public IdeConfig addManualIde(String name, Path executablePath) throws SQLException {
        var path = Objects.requireNonNull(executablePath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("IDE executable is unavailable");
        }
        return ides.saveManual(name, path);
    }

    public Project setDefaultIde(Project project, UUID ideId) throws SQLException {
        if (!ides.isAvailable(ideId)) {
            throw new IllegalArgumentException("IDE is not available");
        }
        var updated = Objects.requireNonNull(project).withDefaultIde(ideId, Instant.now());
        projects.save(updated);
        return updated;
    }

    public Project clearDefaultIde(Project project) throws SQLException {
        var updated = Objects.requireNonNull(project).withDefaultIde(null, Instant.now());
        projects.save(updated);
        return updated;
    }

    public Project openProject(Project project, UUID ideId) throws SQLException, IOException {
        return openProject(project, ideId, true);
    }

    public Project openProject(Project project, UUID ideId, boolean rememberAsDefault)
            throws SQLException, IOException {
        Objects.requireNonNull(project);
        if (project.pathStatus() != PathStatus.NORMAL || !Files.isDirectory(project.path())) {
            throw new IllegalArgumentException("Project path is unavailable");
        }
        var ide = ides.findAvailableById(ideId)
                .orElseThrow(() -> new IllegalArgumentException("IDE is not available"));
        launcher.launch(ide.executablePath(), project.path());
        var now = Instant.now();
        var updated = rememberAsDefault
                ? project.openedWithIde(ide.id(), now)
                : project.openedAt(now);
        projects.save(updated);
        return updated;
    }

    @FunctionalInterface
    interface ProcessLauncher {

        void launch(Path executable, Path projectPath) throws IOException;
    }
}
