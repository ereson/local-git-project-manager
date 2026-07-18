package com.localprojectmanager.infrastructure.ide;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JetBrainsIdeDetectorTest {

    @TempDir
    Path tempDirectory;

    @Test
    void detectsToolboxVersionsAndStandaloneInstallationsInVersionOrder() throws Exception {
        var localAppData = tempDirectory.resolve("local");
        createExecutable(localAppData.resolve(
                "JetBrains/Toolbox/apps/IDEA-U/ch-0/2026.1/bin/idea64.exe"
        ));
        createExecutable(localAppData.resolve(
                "JetBrains/Toolbox/apps/IDEA-U/ch-0/2025.3/bin/idea64.exe"
        ));
        var programFiles = tempDirectory.resolve("program-files");
        createExecutable(programFiles.resolve(
                "JetBrains/PyCharm 2025.2/bin/pycharm64.exe"
        ));
        createExecutable(programFiles.resolve("JetBrains/Unknown/bin/unknown.exe"));

        var detected = new JetBrainsIdeDetector(Map.of(
                "LOCALAPPDATA", localAppData.toString(),
                "ProgramFiles", programFiles.toString()
        )).detect();

        assertEquals(
                java.util.List.of(
                        "IntelliJ IDEA 2026.1",
                        "IntelliJ IDEA 2025.3",
                        "PyCharm 2025.2"
                ),
                detected.stream().map(ide -> ide.name() + " " + ide.version()).toList()
        );
        assertTrue(new JetBrainsIdeDetector(Map.of()).detect().isEmpty());
    }

    @Test
    void detectsCustomInstallationFromWindowsRegistry() throws Exception {
        var installRoot = tempDirectory.resolve("IntelliJ IDEA 2026.1.3");
        createExecutable(installRoot.resolve("bin/idea64.exe"));
        var registryOutput = List.of(
                "HKEY_LOCAL_MACHINE\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Steam",
                "    InstallLocation    REG_SZ    D:\\Steam",
                "HKEY_LOCAL_MACHINE\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\IntelliJ IDEA 2026.1.3",
                "    InstallLocation    REG_SZ    " + installRoot
        );

        var detected = new JetBrainsIdeDetector(
                Map.of(),
                JetBrainsIdeDetector.parseRegistryInstallRoots(registryOutput)
        ).detect();

        assertEquals(List.of("IntelliJ IDEA 2026.1.3"),
                detected.stream().map(ide -> ide.name() + " " + ide.version()).toList());
    }

    private static void createExecutable(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        Files.createFile(path);
    }
}
