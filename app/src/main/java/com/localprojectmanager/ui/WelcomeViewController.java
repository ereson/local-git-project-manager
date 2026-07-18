package com.localprojectmanager.ui;

import javafx.fxml.FXML;

import java.util.Objects;

public final class WelcomeViewController {

    private Runnable onChooseScanDirectory;
    private Runnable onAddManualProject;

    void setOnChooseScanDirectory(Runnable action) {
        onChooseScanDirectory = Objects.requireNonNull(action);
    }

    void setOnAddManualProject(Runnable action) {
        onAddManualProject = Objects.requireNonNull(action);
    }

    @FXML
    private void chooseScanDirectory() {
        Objects.requireNonNull(onChooseScanDirectory, "Navigation callback not configured").run();
    }

    @FXML
    private void addManualProject() {
        Objects.requireNonNull(onAddManualProject, "Manual project callback not configured").run();
    }
}
