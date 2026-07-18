package com.localprojectmanager.infrastructure.git;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class CommandExecutorTest {

    @Test
    void consumesCommandOutputAndEnforcesTimeout() throws Exception {
        var java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        var executor = new CommandExecutor();

        var completed = executor.execute(List.of(java, "-version"), null, Duration.ofSeconds(10));
        var timedOut = executor.execute(List.of(java, "-version"), null, Duration.ZERO);

        assertTrue(completed.successful());
        assertTrue(new String(completed.stderr(), StandardCharsets.UTF_8).contains("version"));
        assertTrue(timedOut.timedOut());
    }
}
