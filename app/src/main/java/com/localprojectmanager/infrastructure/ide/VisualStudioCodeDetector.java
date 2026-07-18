package com.localprojectmanager.infrastructure.ide;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class VisualStudioCodeDetector implements IdeDetector {

    private final Map<String, String> environment;

    public VisualStudioCodeDetector() {
        this(System.getenv());
    }

    VisualStudioCodeDetector(Map<String, String> environment) {
        this.environment = Map.copyOf(environment);
    }

    public List<DetectedIde> detect() {
        var executable = findOnPath()
                .or(() -> findInstalled("LOCALAPPDATA", "Programs", "Microsoft VS Code", "Code.exe"))
                .or(() -> findInstalled("ProgramFiles", "Microsoft VS Code", "Code.exe"))
                .or(() -> findInstalled("ProgramFiles(x86)", "Microsoft VS Code", "Code.exe"));
        // ponytail: common Windows locations cover stable VS Code; add registry lookup if real installs are missed.
        return executable
                .map(path -> List.of(new DetectedIde("Visual Studio Code", null, path)))
                .orElseGet(List::of);
    }

    private Optional<Path> findOnPath() {
        for (var entry : environment.getOrDefault("PATH", "").split(File.pathSeparator)) {
            try {
                var directory = Path.of(unquote(entry)).toAbsolutePath().normalize();
                var direct = directory.resolve("Code.exe");
                if (Files.isRegularFile(direct)) {
                    return Optional.of(direct);
                }
                var parent = directory.getParent();
                if (parent != null
                        && Files.isRegularFile(directory.resolve("code.cmd"))
                        && Files.isRegularFile(parent.resolve("Code.exe"))) {
                    return Optional.of(parent.resolve("Code.exe"));
                }
            } catch (InvalidPathException ignored) {
                // Ignore malformed PATH entries and continue with known install locations.
            }
        }
        return Optional.empty();
    }

    private Optional<Path> findInstalled(String variable, String... relativePath) {
        var base = environment.get(variable);
        if (base == null || base.isBlank()) {
            return Optional.empty();
        }
        try {
            var path = Path.of(base, relativePath).toAbsolutePath().normalize();
            return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
        } catch (InvalidPathException ignored) {
            return Optional.empty();
        }
    }

    private static String unquote(String value) {
        var stripped = value.strip();
        return stripped.length() >= 2 && stripped.startsWith("\"") && stripped.endsWith("\"")
                ? stripped.substring(1, stripped.length() - 1)
                : stripped;
    }
}
