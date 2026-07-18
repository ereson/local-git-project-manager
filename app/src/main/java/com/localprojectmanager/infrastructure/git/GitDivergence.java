package com.localprojectmanager.infrastructure.git;

public record GitDivergence(int ahead, int behind) {

    public GitDivergence {
        if (ahead < 0 || behind < 0) {
            throw new IllegalArgumentException("Ahead and behind counts must not be negative");
        }
    }
}
