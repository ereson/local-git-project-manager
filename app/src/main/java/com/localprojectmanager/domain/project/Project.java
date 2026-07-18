package com.localprojectmanager.domain.project;

import com.localprojectmanager.domain.path.WindowsPathNormalizer;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Project(
        UUID id,
        String displayName,
        String directoryName,
        Path path,
        UUID scanRootId,
        UUID defaultIdeId,
        PullStrategy pullStrategy,
        Instant lastOpenedAt,
        PathStatus pathStatus,
        boolean nestedRepository,
        Path parentRepositoryPath,
        Instant createdAt,
        Instant updatedAt
) {

    public Project {
        Objects.requireNonNull(id, "id");
        displayName = requiredText(displayName, "displayName");
        directoryName = requiredText(directoryName, "directoryName");
        path = normalized(path, "path");
        Objects.requireNonNull(pathStatus, "pathStatus");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");

        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
        if (nestedRepository != (parentRepositoryPath != null)) {
            throw new IllegalArgumentException("Nested projects require exactly one parent repository path");
        }
        if (parentRepositoryPath != null) {
            parentRepositoryPath = normalized(parentRepositoryPath, "parentRepositoryPath");
            if (path.equals(parentRepositoryPath)) {
                throw new IllegalArgumentException("A project cannot be its own parent repository");
            }
        }
    }

    public Project rename(String newDisplayName, Instant changedAt) {
        Objects.requireNonNull(changedAt, "changedAt");
        if (changedAt.isBefore(updatedAt)) {
            throw new IllegalArgumentException("changedAt cannot be before updatedAt");
        }
        return new Project(
                id, newDisplayName, directoryName, path, scanRootId, defaultIdeId,
                pullStrategy, lastOpenedAt, pathStatus, nestedRepository,
                parentRepositoryPath, createdAt, changedAt
        );
    }

    public Project resetDisplayName(Instant changedAt) {
        return rename(directoryName, changedAt);
    }

    public Project withDefaultIde(UUID ideId, Instant changedAt) {
        Objects.requireNonNull(changedAt, "changedAt");
        if (changedAt.isBefore(updatedAt)) {
            throw new IllegalArgumentException("changedAt cannot be before updatedAt");
        }
        return new Project(
                id, displayName, directoryName, path, scanRootId, ideId,
                pullStrategy, lastOpenedAt, pathStatus, nestedRepository,
                parentRepositoryPath, createdAt, changedAt
        );
    }

    public Project openedWithIde(UUID ideId, Instant openedAt) {
        Objects.requireNonNull(ideId, "ideId");
        Objects.requireNonNull(openedAt, "openedAt");
        if (openedAt.isBefore(updatedAt)) {
            throw new IllegalArgumentException("openedAt cannot be before updatedAt");
        }
        return new Project(
                id, displayName, directoryName, path, scanRootId, ideId,
                pullStrategy, openedAt, pathStatus, nestedRepository,
                parentRepositoryPath, createdAt, openedAt
        );
    }

    public Project openedAt(Instant openedAt) {
        Objects.requireNonNull(openedAt, "openedAt");
        if (openedAt.isBefore(updatedAt)) {
            throw new IllegalArgumentException("openedAt cannot be before updatedAt");
        }
        return new Project(
                id, displayName, directoryName, path, scanRootId, defaultIdeId,
                pullStrategy, openedAt, pathStatus, nestedRepository,
                parentRepositoryPath, createdAt, openedAt
        );
    }

    public Project withPullStrategy(PullStrategy strategy, Instant changedAt) {
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(changedAt, "changedAt");
        if (changedAt.isBefore(updatedAt)) {
            throw new IllegalArgumentException("changedAt cannot be before updatedAt");
        }
        return new Project(
                id, displayName, directoryName, path, scanRootId, defaultIdeId,
                strategy, lastOpenedAt, pathStatus, nestedRepository,
                parentRepositoryPath, createdAt, changedAt
        );
    }

    public Project withPathStatus(PathStatus status, Instant changedAt) {
        Objects.requireNonNull(changedAt, "changedAt");
        return new Project(
                id, displayName, directoryName, path, scanRootId, defaultIdeId,
                pullStrategy, lastOpenedAt, Objects.requireNonNull(status), nestedRepository,
                parentRepositoryPath, createdAt, changedAt
        );
    }

    public Project relocated(Path newPath, Instant changedAt) {
        var normalized = normalized(newPath, "newPath");
        var fileName = normalized.getFileName();
        return new Project(
                id, displayName, fileName == null ? normalized.toString() : fileName.toString(),
                normalized, null, defaultIdeId, pullStrategy, lastOpenedAt,
                PathStatus.NORMAL, false, null, createdAt, changedAt
        );
    }

    private static String requiredText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value.strip();
    }

    private static Path normalized(Path value, String name) {
        return WindowsPathNormalizer.normalize(Objects.requireNonNull(value, name));
    }
}
