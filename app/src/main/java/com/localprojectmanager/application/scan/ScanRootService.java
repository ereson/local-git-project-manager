package com.localprojectmanager.application.scan;

import com.localprojectmanager.domain.path.WindowsPathNormalizer;
import com.localprojectmanager.domain.scan.ScanRoot;
import com.localprojectmanager.infrastructure.database.ScanRootRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ScanRootService {

    private final ScanRootRepository repository;

    public ScanRootService(ScanRootRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public List<ScanRoot> list() throws SQLException {
        return repository.findAll();
    }

    public AddResult add(Path path) throws SQLException {
        var normalizedPath = WindowsPathNormalizer.normalize(path);
        if (!Files.isDirectory(normalizedPath)) {
            throw new IllegalArgumentException("所选目录不存在。");
        }
        if (!Files.isReadable(normalizedPath)) {
            throw new IllegalArgumentException("当前用户无权读取所选目录。");
        }
        if (repository.findByPath(normalizedPath).isPresent()) {
            throw new IllegalArgumentException("该扫描目录已经存在。");
        }

        var existing = repository.findAll();
        var overlaps = existing.stream().anyMatch(root -> overlaps(root.path(), normalizedPath));
        var scanRoot = new ScanRoot(
                UUID.randomUUID(), normalizedPath, true, null, null, null, Instant.now()
        );
        repository.save(scanRoot);
        return new AddResult(scanRoot, overlaps);
    }

    public boolean remove(UUID id) throws SQLException {
        return repository.delete(id);
    }

    private static boolean overlaps(Path first, Path second) {
        var firstKey = WindowsPathNormalizer.comparisonKey(first);
        var secondKey = WindowsPathNormalizer.comparisonKey(second);
        return contains(firstKey, secondKey) || contains(secondKey, firstKey);
    }

    private static boolean contains(String parent, String child) {
        var prefix = parent.endsWith("/") ? parent : parent + "/";
        return child.startsWith(prefix);
    }

    public record AddResult(ScanRoot scanRoot, boolean overlapsExisting) {
    }
}

