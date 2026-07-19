package com.localprojectmanager.ui;

import com.localprojectmanager.domain.project.PathStatus;
import com.localprojectmanager.domain.project.Project;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProjectHomeViewControllerTest {

    @Test
    void launchesOnlyNormalProjectsWithAnAvailableDefaultIde() {
        var ideId = UUID.randomUUID();

        assertTrue(ProjectHomeViewController.canLaunch(project(PathStatus.NORMAL, ideId), Map.of(ideId, "IDE")));
        assertFalse(ProjectHomeViewController.canLaunch(project(PathStatus.NORMAL, null), Map.of(ideId, "IDE")));
        assertFalse(ProjectHomeViewController.canLaunch(project(PathStatus.NORMAL, ideId), Map.of()));
        assertFalse(ProjectHomeViewController.canLaunch(project(PathStatus.UNAVAILABLE, ideId), Map.of(ideId, "IDE")));
    }

    @Test
    void opensSelectedTableProjectOnlyWithEnter() {
        assertTrue(ProjectHomeViewController.opensSelectedProject(KeyCode.ENTER));
        assertFalse(ProjectHomeViewController.opensSelectedProject(KeyCode.SPACE));
    }

    private static Project project(PathStatus status, UUID ideId) {
        var now = Instant.parse("2026-07-19T00:00:00Z");
        return new Project(
                UUID.randomUUID(), "demo", "demo", Path.of("demo"), null,
                ideId, null, null, status, false, null, now, now
        );
    }
}
