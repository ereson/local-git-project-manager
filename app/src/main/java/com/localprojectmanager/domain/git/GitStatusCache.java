package com.localprojectmanager.domain.git;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record GitStatusCache(
        UUID projectId,
        String currentBranch,
        int uncommittedFileCount,
        String latestCommitHash,
        String latestCommitMessage,
        Instant latestCommitTime,
        String remoteUrl,
        String upstreamBranch,
        Integer aheadCount,
        Integer behindCount,
        int conflictFileCount,
        boolean hasConflict,
        Instant remoteStatusUpdatedAt,
        Instant localStatusUpdatedAt,
        RefreshStatus refreshStatus,
        String refreshError
) {

    public GitStatusCache {
        Objects.requireNonNull(projectId);
        Objects.requireNonNull(refreshStatus);
        if (uncommittedFileCount < 0) {
            throw new IllegalArgumentException("Uncommitted file count must not be negative");
        }
        if (conflictFileCount < 0) {
            throw new IllegalArgumentException("Conflict file count must not be negative");
        }
    }

    public static GitStatusCache pending(UUID projectId) {
        return new GitStatusCache(
                projectId, null, 0, null, null, null, null,
                null, null, null, 0, false, null, null, RefreshStatus.PENDING, null
        );
    }

    public GitStatusCache withRemoteStatus(
            String upstream,
            Integer ahead,
            Integer behind,
            Instant updatedAt
    ) {
        return new GitStatusCache(
                projectId, currentBranch, uncommittedFileCount,
                latestCommitHash, latestCommitMessage, latestCommitTime, remoteUrl,
                upstream,
                ahead,
                behind,
                conflictFileCount,
                hasConflict,
                updatedAt,
                localStatusUpdatedAt, refreshStatus, refreshError
        );
    }

    public GitStatusCache withConflict(int fileCount, boolean conflict) {
        return new GitStatusCache(
                projectId, currentBranch, uncommittedFileCount,
                latestCommitHash, latestCommitMessage, latestCommitTime, remoteUrl,
                upstreamBranch, aheadCount, behindCount, fileCount, conflict,
                remoteStatusUpdatedAt, localStatusUpdatedAt, refreshStatus, refreshError
        );
    }

    public enum RefreshStatus {
        PENDING,
        REFRESHING,
        SUCCESS,
        FAILED
    }
}
