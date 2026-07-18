package com.localprojectmanager.domain.project;

public enum PullStrategy {
    REBASE("Rebase"),
    MERGE("Merge"),
    GIT_CONFIG("遵循 Git 配置");

    private final String displayName;

    PullStrategy(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
