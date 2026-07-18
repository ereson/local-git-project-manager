package com.localprojectmanager.application.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class ProjectLaunchService {

    private final Launcher launcher;

    public ProjectLaunchService(Launcher launcher) {
        this.launcher = Objects.requireNonNull(launcher);
    }

    public void openInExplorer(Path projectPath) throws IOException {
        launcher.openInExplorer(requireDirectory(projectPath));
    }

    public void openInTerminal(Path projectPath) throws IOException {
        launcher.openInTerminal(requireDirectory(projectPath));
    }

    private static Path requireDirectory(Path projectPath) {
        Objects.requireNonNull(projectPath);
        if (!Files.isDirectory(projectPath)) {
            throw new IllegalArgumentException("Project path is unavailable");
        }
        return projectPath;
    }

    public interface Launcher {

        void openInExplorer(Path projectPath) throws IOException;

        void openInTerminal(Path projectPath) throws IOException;
    }
}
