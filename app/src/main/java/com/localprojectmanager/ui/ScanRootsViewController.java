package com.localprojectmanager.ui;

import com.localprojectmanager.application.scan.GitRepositoryScanner;
import com.localprojectmanager.domain.scan.ScanRoot;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.stage.DirectoryChooser;

import java.util.Objects;
import java.util.function.Consumer;

public final class ScanRootsViewController {

    @FXML
    private ListView<ScanRoot> scanRootsList;

    @FXML
    private Label messageLabel;

    @FXML
    private Button removeButton;

    @FXML
    private Button startScanButton;

    @FXML
    private CheckBox defaultIgnoreRulesCheckBox;

    @FXML
    private Button addButton;

    private final BooleanProperty scanning = new SimpleBooleanProperty(false);
    private Runnable onBack;
    private Consumer<GitRepositoryScanner.ScanResult> onScanCompleted;
    private ScanRootsViewModel viewModel;
    private Task<GitRepositoryScanner.ScanResult> scanTask;

    void setOnBack(Runnable action) {
        onBack = Objects.requireNonNull(action);
    }

    void setOnScanCompleted(Consumer<GitRepositoryScanner.ScanResult> action) {
        onScanCompleted = Objects.requireNonNull(action);
    }

    void setViewModel(ScanRootsViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel);
        scanRootsList.setItems(viewModel.scanRoots());
        scanRootsList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(ScanRoot item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.path().toString());
            }
        });
        messageLabel.textProperty().bind(viewModel.messageProperty());
        defaultIgnoreRulesCheckBox.selectedProperty()
                .bindBidirectional(viewModel.useDefaultIgnoreRulesProperty());
        removeButton.disableProperty().bind(
                scanRootsList.getSelectionModel().selectedItemProperty().isNull().or(scanning)
        );
        addButton.disableProperty().bind(scanning);
        defaultIgnoreRulesCheckBox.disableProperty().bind(scanning);
        scanRootsList.disableProperty().bind(scanning);
        startScanButton.disableProperty().bind(Bindings.isEmpty(viewModel.scanRoots()));
        viewModel.load();
    }

    @FXML
    private void goBack() {
        if (scanTask != null) {
            scanTask.cancel(true);
        }
        Objects.requireNonNull(onBack, "Navigation callback not configured").run();
    }

    @FXML
    private void addDirectory() {
        var chooser = new DirectoryChooser();
        chooser.setTitle("选择扫描目录");
        var selected = chooser.showDialog(scanRootsList.getScene().getWindow());
        if (selected != null) {
            Objects.requireNonNull(viewModel, "ViewModel not configured").add(selected.toPath());
        }
    }

    @FXML
    private void removeSelected() {
        Objects.requireNonNull(viewModel, "ViewModel not configured")
                .remove(scanRootsList.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void startScan() {
        if (scanTask != null) {
            scanTask.cancel(true);
            return;
        }

        var model = Objects.requireNonNull(viewModel, "ViewModel not configured");
        var roots = model.scanRoots().stream().map(ScanRoot::path).toList();
        var task = new Task<GitRepositoryScanner.ScanResult>() {
            @Override
            protected GitRepositoryScanner.ScanResult call() {
                return GitRepositoryScanner.scan(
                        roots,
                        model.useDefaultIgnoreRules(),
                        model.customIgnoreRules(),
                        this::isCancelled
                );
            }
        };
        scanTask = task;
        scanning.set(true);
        startScanButton.setText("取消扫描");
        model.scanStarted();
        task.setOnSucceeded(event -> {
            var result = task.getValue();
            model.scanCompleted(result);
            finishScan();
            Objects.requireNonNull(onScanCompleted, "Scan callback not configured").accept(result);
        });
        task.setOnCancelled(event -> {
            model.scanCancelled();
            finishScan();
        });
        task.setOnFailed(event -> {
            model.scanFailed();
            finishScan();
        });
        Thread.ofVirtual().name("git-repository-scan").start(task);
    }

    private void finishScan() {
        scanTask = null;
        scanning.set(false);
        startScanButton.setText("开始扫描");
    }
}
