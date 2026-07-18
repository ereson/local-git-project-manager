package com.localprojectmanager.infrastructure.ide;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class JetBrainsIdeDetector implements IdeDetector {

    private static final Map<String, String> PRODUCTS = Map.ofEntries(
            Map.entry("idea64.exe", "IntelliJ IDEA"),
            Map.entry("pycharm64.exe", "PyCharm"),
            Map.entry("webstorm64.exe", "WebStorm"),
            Map.entry("goland64.exe", "GoLand"),
            Map.entry("clion64.exe", "CLion"),
            Map.entry("phpstorm64.exe", "PhpStorm"),
            Map.entry("rider64.exe", "Rider"),
            Map.entry("rubymine64.exe", "RubyMine"),
            Map.entry("datagrip64.exe", "DataGrip"),
            Map.entry("studio64.exe", "Android Studio")
    );
    private static final Pattern VERSION = Pattern.compile("(?<!\\d)(\\d+(?:\\.\\d+)+)(?!\\d)");
    private static final Pattern INSTALL_LOCATION = Pattern.compile(
            "^\\s*InstallLocation\\s+REG_SZ\\s+(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final List<String> UNINSTALL_KEYS = List.of(
            "HKLM\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
            "HKLM\\Software\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall"
    );

    private final Map<String, String> environment;
    private final List<Path> installRoots;

    public JetBrainsIdeDetector() {
        this(System.getenv(), registryInstallRoots());
    }

    JetBrainsIdeDetector(Map<String, String> environment) {
        this(environment, List.of());
    }

    JetBrainsIdeDetector(Map<String, String> environment, List<Path> installRoots) {
        this.environment = Map.copyOf(environment);
        this.installRoots = List.copyOf(installRoots);
    }

    @Override
    public List<DetectedIde> detect() {
        var roots = new LinkedHashMap<Path, Integer>();
        addRoot(roots, "LOCALAPPDATA", 6, "JetBrains", "Toolbox", "apps");
        addRoot(roots, "USERPROFILE", 6, "AppData", "Local", "JetBrains", "Toolbox", "apps");
        addRoot(roots, "ProgramFiles", 4, "JetBrains");
        addRoot(roots, "ProgramFiles(x86)", 4, "JetBrains");
        addRoot(roots, "LOCALAPPDATA", 4, "Programs");
        addRoot(roots, "ProgramFiles", 4, "Android");
        installRoots.forEach(root -> addRoot(roots, root, 2));

        var detected = new LinkedHashMap<Path, DetectedIde>();
        roots.forEach((root, depth) -> scan(root, depth, detected));
        return detected.values().stream()
                .sorted(Comparator.comparing(DetectedIde::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(
                                DetectedIde::version,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        ))
                .toList();
    }

    private void addRoot(
            Map<Path, Integer> roots,
            String variable,
            int depth,
            String... relativePath
    ) {
        var base = environment.get(variable);
        if (base == null || base.isBlank()) {
            return;
        }
        try {
            addRoot(roots, Path.of(base, relativePath), depth);
        } catch (InvalidPathException | SecurityException ignored) {
            // Ignore invalid or inaccessible installation roots.
        }
    }

    private static void addRoot(Map<Path, Integer> roots, Path path, int depth) {
        try {
            var root = path.toAbsolutePath().normalize();
            if (Files.isDirectory(root)) {
                roots.putIfAbsent(root, depth);
            }
        } catch (InvalidPathException | SecurityException ignored) {
            // Ignore invalid or inaccessible installation roots.
        }
    }

    private static List<Path> registryInstallRoots() {
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            return List.of();
        }
        var roots = new ArrayList<Path>();
        for (var key : UNINSTALL_KEYS) {
            try {
                var process = new ProcessBuilder(
                        "reg.exe", "query", key, "/s", "/v", "InstallLocation"
                ).redirectErrorStream(true).start();
                var charset = Charset.forName(System.getProperty(
                        "sun.jnu.encoding", Charset.defaultCharset().name()
                ));
                try (var reader = new BufferedReader(new InputStreamReader(
                        process.getInputStream(), charset
                ))) {
                    roots.addAll(parseRegistryInstallRoots(reader.lines().toList()));
                }
                process.waitFor();
            } catch (IOException ignored) {
                // Registry lookup is an optional fallback for custom installations.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return roots;
    }

    static List<Path> parseRegistryInstallRoots(List<String> lines) {
        var roots = new ArrayList<Path>();
        var productKey = false;
        for (var line : lines) {
            var trimmed = line.trim();
            if (trimmed.startsWith("HKEY_")) {
                var key = trimmed.toLowerCase(Locale.ROOT);
                productKey = PRODUCTS.values().stream()
                        .map(name -> name.toLowerCase(Locale.ROOT))
                        .anyMatch(key::contains);
                continue;
            }
            var matcher = INSTALL_LOCATION.matcher(line);
            if (productKey && matcher.matches()) {
                try {
                    roots.add(Path.of(matcher.group(1)));
                } catch (InvalidPathException ignored) {
                    // Ignore malformed registry values.
                }
            }
        }
        return roots;
    }

    private static void scan(
            Path root,
            int depth,
            Map<Path, DetectedIde> detected
    ) {
        try (var paths = Files.find(root, depth, (path, attributes) ->
                attributes.isRegularFile() && PRODUCTS.containsKey(
                        path.getFileName().toString().toLowerCase(Locale.ROOT)
                ))) {
            paths.forEach(path -> {
                var executable = path.toAbsolutePath().normalize();
                detected.putIfAbsent(executable, new DetectedIde(
                        PRODUCTS.get(path.getFileName().toString().toLowerCase(Locale.ROOT)),
                        version(executable, root),
                        executable
                ));
            });
        } catch (IOException | UncheckedIOException | SecurityException ignored) {
            // One inaccessible root must not hide IDEs found in other roots.
        }
    }

    private static String version(Path executable, Path root) {
        for (var directory = executable.getParent();
             directory != null && directory.startsWith(root);
             directory = directory.getParent()) {
            var matcher = VERSION.matcher(directory.getFileName().toString());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }
}
