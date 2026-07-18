package com.localprojectmanager.domain.update;

public record UpdateCheckResult(Status status, UpdateManifest update, String message) {

    public enum Status {
        AVAILABLE,
        CURRENT,
        FAILED
    }
}
