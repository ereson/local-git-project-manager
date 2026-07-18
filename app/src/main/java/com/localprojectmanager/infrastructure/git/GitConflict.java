package com.localprojectmanager.infrastructure.git;

public record GitConflict(Type type, int fileCount) {

    public GitConflict {
        if (fileCount < 0) {
            throw new IllegalArgumentException("Conflict file count must not be negative");
        }
    }

    public boolean hasConflict() {
        return type != Type.NONE || fileCount > 0;
    }

    public enum Type {
        NONE,
        MERGE,
        REBASE,
        CHERRY_PICK,
        UNKNOWN
    }
}
