package com.localprojectmanager.application.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ProjectLaunchServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void delegatesExistingDirectoryWithoutLaunchingWindowsPrograms() throws Exception {
        var projectPath = Files.createDirectory(tempDirectory.resolve("中文 project"));
        var calls = new ArrayList<String>();
        var service = new ProjectLaunchService(new ProjectLaunchService.Launcher() {
            @Override
            public void openInExplorer(Path path) {
                calls.add("explorer:" + path);
            }

            @Override
            public void openInTerminal(Path path) {
                calls.add("terminal:" + path);
            }
        });

        service.openInExplorer(projectPath);
        service.openInTerminal(projectPath);

        assertEquals(List.of("explorer:" + projectPath, "terminal:" + projectPath), calls);
    }

    @Test
    void rejectsMissingPathBeforeCallingLauncher() {
        var service = new ProjectLaunchService(failingLauncher());

        assertThrows(IllegalArgumentException.class,
                () -> service.openInExplorer(tempDirectory.resolve("missing")));
    }

    @Test
    void rejectsFileBeforeCallingLauncher() throws Exception {
        var service = new ProjectLaunchService(failingLauncher());

        assertThrows(IllegalArgumentException.class,
                () -> service.openInTerminal(Files.createFile(tempDirectory.resolve("file.txt"))));
    }

    private static ProjectLaunchService.Launcher failingLauncher() {
        return new ProjectLaunchService.Launcher() {
            @Override
            public void openInExplorer(Path projectPath) throws IOException {
                throw new IOException("launcher must not be called");
            }

            @Override
            public void openInTerminal(Path projectPath) throws IOException {
                throw new IOException("launcher must not be called");
            }
        };
    }
}
