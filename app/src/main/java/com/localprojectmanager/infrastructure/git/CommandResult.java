package com.localprojectmanager.infrastructure.git;

import java.time.Duration;

public record CommandResult(
        int exitCode,
        byte[] stdout,
        byte[] stderr,
        Duration duration,
        boolean timedOut
) {

    public boolean successful() {
        return !timedOut && exitCode == 0;
    }
}
