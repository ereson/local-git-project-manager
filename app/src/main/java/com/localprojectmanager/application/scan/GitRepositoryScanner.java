package com.localprojectmanager.application.scan;

import com.localprojectmanager.domain.scan.DefaultIgnoreRules;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

public final class GitRepositoryScanner {

    private GitRepositoryScanner() {
    }

    public static DiscoveredRepository inspect(Path directory) {
        Objects.requireNonNull(directory, "directory");
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Project path is not a directory");
        }
        var gitMetadata = directory.resolve(".git");
        if (!Files.isDirectory(gitMetadata) && !Files.isRegularFile(gitMetadata)) {
            throw new IllegalArgumentException("Selected directory is not a Git repository");
        }
        return new DiscoveredRepository(directory.toAbsolutePath().normalize(), null);
    }

    public static ScanResult scan(
            List<Path> roots,
            boolean useDefaultIgnoreRules,
            BooleanSupplier cancelled
    ) {
        return scan(roots, useDefaultIgnoreRules, List.of(), cancelled);
    }

    public static ScanResult scan(
            List<Path> roots,
            boolean useDefaultIgnoreRules,
            List<String> customIgnoreDirectoryNames,
            BooleanSupplier cancelled
    ) {
        Objects.requireNonNull(roots, "roots");
        var customIgnores = customIgnoreDirectoryNames.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.strip().toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Objects.requireNonNull(cancelled, "cancelled");
        var repositories = new LinkedHashSet<Path>();
        var failedPaths = new LinkedHashSet<Path>();

        for (var root : roots) {
            Objects.requireNonNull(root, "root");
            if (isCancelled(cancelled)) {
                break;
            }
            if (!Files.isDirectory(root)) {
                failedPaths.add(root);
                continue;
            }
            try {
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(
                            Path directory,
                            BasicFileAttributes attributes
                    ) {
                        if (isCancelled(cancelled)) {
                            return FileVisitResult.TERMINATE;
                        }
                        if (useDefaultIgnoreRules && DefaultIgnoreRules.shouldIgnore(directory)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        var name = directory.getFileName();
                        if (name != null && customIgnores.contains(
                                name.toString().toLowerCase(java.util.Locale.ROOT)
                        )) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        var gitMetadata = directory.resolve(".git");
                        if (Files.isDirectory(gitMetadata) || Files.isRegularFile(gitMetadata)) {
                            repositories.add(directory);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path path, IOException exception) {
                        failedPaths.add(path);
                        return isCancelled(cancelled)
                                ? FileVisitResult.TERMINATE
                                : FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException | SecurityException exception) {
                failedPaths.add(root);
            }
        }

        return new ScanResult(
                describe(repositories),
                List.copyOf(failedPaths),
                isCancelled(cancelled)
        );
    }

    private static List<DiscoveredRepository> describe(Set<Path> repositories) {
        // ponytail: O(n²) is simpler and sufficient for local project counts; index if scans reach thousands.
        return repositories.stream()
                .map(path -> new DiscoveredRepository(
                        path,
                        repositories.stream()
                                .filter(candidate -> !candidate.equals(path) && path.startsWith(candidate))
                                .max(Comparator.comparingInt(Path::getNameCount))
                                .orElse(null)
                ))
                .toList();
    }

    private static boolean isCancelled(BooleanSupplier cancelled) {
        return Thread.currentThread().isInterrupted() || cancelled.getAsBoolean();
    }

    public record DiscoveredRepository(Path path, Path parentRepositoryPath) {

        public DiscoveredRepository {
            Objects.requireNonNull(path, "path");
        }

        public boolean nestedRepository() {
            return parentRepositoryPath != null;
        }
    }

    public record ScanResult(
            List<DiscoveredRepository> repositories,
            List<Path> failedPaths,
            boolean cancelled
    ) {

        public ScanResult {
            repositories = List.copyOf(repositories);
            failedPaths = List.copyOf(failedPaths);
        }
    }
}
