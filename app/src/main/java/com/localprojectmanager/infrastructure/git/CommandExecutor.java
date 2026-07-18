package com.localprojectmanager.infrastructure.git;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;

public final class CommandExecutor {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(CommandExecutor.class);

    public CommandResult execute(
            List<String> command,
            Path workingDirectory,
            Duration timeout
    ) throws IOException, InterruptedException {
        var arguments = List.copyOf(command);
        if (arguments.isEmpty()) {
            throw new IllegalArgumentException("Command must not be empty");
        }
        Objects.requireNonNull(timeout);
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must not be negative");
        }

        var builder = new ProcessBuilder(arguments);
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile());
        }
        var started = System.nanoTime();
        var process = builder.start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var stdout = executor.submit(process.getInputStream()::readAllBytes);
            var stderr = executor.submit(process.getErrorStream()::readAllBytes);
            var finished = process.waitFor(timeout.toNanos(), TimeUnit.NANOSECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor();
            }
            var result = new CommandResult(
                    finished ? process.exitValue() : -1,
                    read(stdout),
                    read(stderr),
                    Duration.ofNanos(System.nanoTime() - started),
                    !finished
            );
            if (!result.successful()) {
                LOG.error("Git command failed: timedOut={}, exitCode={}, workingDirectory={}",
                        result.timedOut(), result.exitCode(), workingDirectory);
            }
            return result;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static byte[] read(Future<byte[]> output) throws IOException, InterruptedException {
        try {
            return output.get();
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to read command output", exception.getCause());
        }
    }
}
