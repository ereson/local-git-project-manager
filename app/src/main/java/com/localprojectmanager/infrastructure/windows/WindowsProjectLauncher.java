package com.localprojectmanager.infrastructure.windows;

import com.localprojectmanager.application.project.ProjectLaunchService;

import java.io.IOException;
import java.nio.file.Path;

public final class WindowsProjectLauncher implements ProjectLaunchService.Launcher {

    @Override
    public void openInExplorer(Path projectPath) throws IOException {
        new ProcessBuilder("explorer.exe", projectPath.toString()).start();
    }

    @Override
    public void openInTerminal(Path projectPath) throws IOException {
        new ProcessBuilder("powershell.exe", "-NoExit")
                .directory(projectPath.toFile())
                .start();
    }
}
