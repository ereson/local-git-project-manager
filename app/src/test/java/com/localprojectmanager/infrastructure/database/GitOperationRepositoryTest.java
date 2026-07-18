package com.localprojectmanager.infrastructure.database;

import com.localprojectmanager.domain.git.GitOperationRecord;
import com.localprojectmanager.domain.project.PathStatus;
import com.localprojectmanager.domain.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GitOperationRepositoryTest {

    @TempDir
    Path tempDirectory;

    @Test
    void keepsOnlyTheLatestOperationPerProject() throws Exception {
        var database = new Database(tempDirectory.resolve("data/app.db"));
        database.initialize();
        var projectId = UUID.randomUUID();
        var created = Instant.parse("2026-01-01T00:00:00Z");
        new ProjectRepository(database).save(new Project(
                projectId, "demo", "demo", tempDirectory.resolve("demo"),
                null, null, null, null, PathStatus.NORMAL,
                false, null, created, created
        ));
        var repository = new GitOperationRepository(database);
        var first = operation(projectId, GitOperationRecord.Type.FETCH, "first");
        var latest = operation(projectId, GitOperationRecord.Type.PULL, "latest");

        repository.save(first);
        repository.save(latest);

        assertEquals(latest, repository.findByProjectId(projectId).orElseThrow());
    }

    private static GitOperationRecord operation(
            UUID projectId,
            GitOperationRecord.Type type,
            String summary
    ) {
        var time = Instant.parse("2026-07-18T12:00:00Z");
        return new GitOperationRecord(
                projectId, type, time, time, GitOperationRecord.Status.SUCCESS, summary, null
        );
    }
}
