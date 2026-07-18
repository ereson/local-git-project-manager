package com.localprojectmanager.infrastructure.ide;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VisualStudioCodeDetectorTest {

    @TempDir
    Path tempDirectory;

    @Test
    void prefersPathThenFallsBackToUserInstallAndIgnoresMissingInstallations() throws Exception {
        var pathInstall = tempDirectory.resolve("path-code");
        var bin = Files.createDirectories(pathInstall.resolve("bin"));
        Files.createFile(bin.resolve("code.cmd"));
        var pathExecutable = Files.createFile(pathInstall.resolve("Code.exe"));
        var localAppData = tempDirectory.resolve("local");
        var localExecutable = localAppData.resolve("Programs/Microsoft VS Code/Code.exe");
        Files.createDirectories(localExecutable.getParent());
        Files.createFile(localExecutable);

        var detectedFromPath = new VisualStudioCodeDetector(Map.of(
                "PATH", bin.toString(),
                "LOCALAPPDATA", localAppData.toString()
        )).detect();
        assertEquals("Visual Studio Code", detectedFromPath.getFirst().name());
        assertEquals(pathExecutable.toAbsolutePath(), detectedFromPath.getFirst().executablePath());

        var detectedFromUserInstall = new VisualStudioCodeDetector(Map.of(
                "LOCALAPPDATA", localAppData.toString()
        )).detect();
        assertEquals(localExecutable.toAbsolutePath(), detectedFromUserInstall.getFirst().executablePath());
        assertTrue(new VisualStudioCodeDetector(Map.of()).detect().isEmpty());
    }
}
