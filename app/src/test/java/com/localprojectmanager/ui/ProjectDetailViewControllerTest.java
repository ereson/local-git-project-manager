package com.localprojectmanager.ui;

import com.localprojectmanager.domain.git.GitOperationRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProjectDetailViewControllerTest {

    @Test
    void formatsGitOperationAsUserFacingChineseText() {
        var time = Instant.parse("2026-07-19T00:00:00Z");
        var operation = new GitOperationRecord(
                UUID.randomUUID(), GitOperationRecord.Type.SWITCH_BRANCH,
                time, time, GitOperationRecord.Status.CONFLICT, null, null
        );

        var text = ProjectDetailViewController.operationText(operation);

        assertTrue(text.startsWith("最近操作：切换分支 · 存在冲突 · 2026-07-19"));
    }
}
