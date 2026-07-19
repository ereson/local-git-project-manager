package com.localprojectmanager.ui;

import com.localprojectmanager.application.project.ProjectImportService;
import com.localprojectmanager.application.scan.GitRepositoryScanner.DiscoveredRepository;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.util.Objects;

public final class ScanResultsViewController {

    @FXML
    private Label summaryLabel;

    @FXML
    private ListView<DiscoveredRepository> resultsList;

    @FXML
    private Label warningLabel;

    @FXML
    private Label selectedLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Button importButton;

    private Runnable onBack;
    private Runnable onImportCompleted;
    private ScanResultsViewModel viewModel;
    private ProjectImportService importService;

    void setOnBack(Runnable action) {
        onBack = Objects.requireNonNull(action);
    }

    void setOnImportCompleted(Runnable action) {
        onImportCompleted = Objects.requireNonNull(action);
    }

    void setImportService(ProjectImportService importService) {
        this.importService = Objects.requireNonNull(importService);
    }

    void setViewModel(ScanResultsViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel);
        summaryLabel.setText("已发现 " + viewModel.repositories().size() + " 个 Git 项目");
        resultsList.setItems(viewModel.repositories());
        resultsList.setCellFactory(ignored -> new RepositoryCell());
        selectedLabel.textProperty().bind(
                viewModel.selectedCountProperty().asString("已选择 %d 个项目")
        );
        importButton.disableProperty().bind(viewModel.selectedCountProperty().isEqualTo(0));
        warningLabel.setText(viewModel.warningText());
        warningLabel.setVisible(!viewModel.warningText().isEmpty());
        warningLabel.setManaged(warningLabel.isVisible());
        if (warningLabel.isVisible()) {
            warningLabel.setTooltip(new Tooltip(viewModel.warningDetails()));
        }
        try {
            viewModel.markImported(Objects.requireNonNull(
                    importService,
                    "Import service not configured"
            ).findAlreadyImported(viewModel.repositories()));
            resultsList.refresh();
        } catch (SQLException exception) {
            statusLabel.setText("无法检查已导入项目，请稍后重试。");
        }
    }

    @FXML
    private void goBack() {
        Objects.requireNonNull(onBack, "Navigation callback not configured").run();
    }

    @FXML
    private void selectAll() {
        Objects.requireNonNull(viewModel, "ViewModel not configured").selectAll();
        resultsList.refresh();
    }

    @FXML
    private void clearSelection() {
        Objects.requireNonNull(viewModel, "ViewModel not configured").clearSelection();
        resultsList.refresh();
    }

    @FXML
    private void importSelected() {
        var selected = Objects.requireNonNull(viewModel, "ViewModel not configured")
                .selectedRepositories();
        try {
            var result = Objects.requireNonNull(importService, "Import service not configured")
                    .importRepositories(selected);
            viewModel.markImported(selected);
            resultsList.refresh();
            statusLabel.setText(
                    "已导入 " + result.importedCount() + " 个项目，跳过 "
                            + result.duplicateCount() + " 个重复项目。"
            );
            Objects.requireNonNull(
                    onImportCompleted,
                    "Import completion callback not configured"
            ).run();
        } catch (SQLException exception) {
            statusLabel.setText("项目导入失败，本次没有写入任何项目。");
        }
    }

    private final class RepositoryCell extends ListCell<DiscoveredRepository> {

        private final CheckBox selected = new CheckBox();
        private final Label name = new Label();
        private final Label path = new Label();
        private final Label nested = new Label();
        private final Label imported = new Label("已导入");
        private final VBox details = new VBox(3, name, path, nested);
        private final Region spacer = new Region();
        private final HBox content = new HBox(
                12,
                selected,
                details,
                spacer,
                imported
        );

        private RepositoryCell() {
            name.getStyleClass().add("result-name");
            path.getStyleClass().add("result-path");
            nested.getStyleClass().add("nested-text");
            imported.getStyleClass().add("imported-text");
            HBox.setHgrow(details, Priority.ALWAYS);
            HBox.setHgrow(spacer, Priority.ALWAYS);
            content.setMaxWidth(Double.MAX_VALUE);
        }

        @Override
        protected void updateItem(DiscoveredRepository repository, boolean empty) {
            super.updateItem(repository, empty);
            if (empty || repository == null) {
                setGraphic(null);
                return;
            }

            var directoryName = repository.path().getFileName();
            name.setText(directoryName == null ? repository.path().toString() : directoryName.toString());
            path.setText(repository.path().toString());
            var parent = repository.parentRepositoryPath();
            nested.setText(parent == null ? "" : "⚠ 嵌套项目：位于 " + parent.getFileName() + " 中");
            nested.setVisible(parent != null);
            nested.setManaged(parent != null);
            var alreadyImported = viewModel.isImported(repository);
            selected.setSelected(viewModel.isSelected(repository));
            selected.setDisable(alreadyImported);
            selected.setAccessibleText("选择项目 " + name.getText());
            selected.setOnAction(event -> viewModel.setSelected(repository, selected.isSelected()));
            imported.setVisible(alreadyImported);
            imported.setManaged(alreadyImported);
            setGraphic(content);
        }
    }
}
