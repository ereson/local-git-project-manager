package com.localprojectmanager.ui;

import com.localprojectmanager.application.ide.IdeService;
import com.localprojectmanager.application.git.GitStatusService;
import com.localprojectmanager.domain.git.GitStatusCache;
import com.localprojectmanager.application.project.ProjectImportService;
import com.localprojectmanager.application.settings.ApplicationSettingsService;
import com.localprojectmanager.application.settings.ApplicationSettingsService.ViewMode;
import com.localprojectmanager.domain.project.PathStatus;
import com.localprojectmanager.domain.project.Project;
import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.ScrollPane;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class ProjectHomeViewController {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    @FXML
    private Label summaryLabel;

    @FXML
    private TextField searchField;

    @FXML
    private FlowPane cardsPane;

    @FXML
    private Label statusLabel;

    @FXML private ToggleButton cardViewButton;
    @FXML private ToggleButton tableViewButton;
    @FXML private ScrollPane cardsScroll;
    @FXML private TableView<Project> projectTable;
    @FXML private TableColumn<Project, String> nameColumn;
    @FXML private TableColumn<Project, String> pathColumn;
    @FXML private TableColumn<Project, String> branchColumn;
    @FXML private TableColumn<Project, String> changesColumn;
    @FXML private TableColumn<Project, String> ideColumn;
    @FXML private TableColumn<Project, String> openedColumn;
    @FXML private TableColumn<Project, String> stateColumn;

    private ProjectImportService projectService;
    private IdeService ideService;
    private Map<UUID, String> ideNames = Map.of();
    private Map<UUID, GitStatusCache> gitStatuses = Map.of();
    private GitStatusService gitStatusService;
    private ApplicationSettingsService settingsService;
    private Runnable onAddScanDirectory;
    private Runnable onAddManualProject;
    private Runnable onOpenSettings;
    private Consumer<Project> onOpenProject;

    @FXML
    private void initialize() {
        var group = new ToggleGroup();
        cardViewButton.setToggleGroup(group);
        tableViewButton.setToggleGroup(group);
        nameColumn.setCellValueFactory(cell -> text(cell.getValue().displayName()));
        pathColumn.setCellValueFactory(cell -> text(cell.getValue().path().toString()));
        branchColumn.setCellValueFactory(cell -> text(cacheValue(cell.getValue()).currentBranch()));
        changesColumn.setCellValueFactory(cell -> text(cacheValue(cell.getValue()).uncommittedFileCount() + " 个"));
        ideColumn.setCellValueFactory(cell -> text(cell.getValue().defaultIdeId() == null
                ? "未设置" : ideNames.getOrDefault(cell.getValue().defaultIdeId(), "不可用")));
        openedColumn.setCellValueFactory(cell -> text(cell.getValue().lastOpenedAt() == null
                ? "从未打开" : DATE_TIME.format(cell.getValue().lastOpenedAt())));
        stateColumn.setCellValueFactory(cell -> text(pathStateText(cell.getValue().pathStatus())));
        projectTable.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                var selected = projectTable.getSelectionModel().getSelectedItem();
                if (selected != null && onOpenProject != null) onOpenProject.accept(selected);
            }
        });
        searchField.textProperty().addListener((ignored, oldValue, newValue) -> {
            if (projectService != null) {
                showProjects(newValue);
            }
        });
    }

    void setSettingsService(ApplicationSettingsService settingsService) {
        this.settingsService = Objects.requireNonNull(settingsService);
        setView(settingsService.viewMode());
    }

    void setProjectService(ProjectImportService projectService) {
        this.projectService = Objects.requireNonNull(projectService);
        refreshProjects();
    }

    void setIdeService(IdeService ideService) {
        this.ideService = Objects.requireNonNull(ideService);
        try {
            ideNames = ideService.listAvailableIdes().stream()
                    .collect(Collectors.toUnmodifiableMap(ide -> ide.id(), Object::toString));
        } catch (SQLException exception) {
            ideNames = Map.of();
        }
    }

    void setGitStatusService(GitStatusService gitStatusService) {
        this.gitStatusService = Objects.requireNonNull(gitStatusService);
        loadGitStatuses();
        gitStatusService.refreshAll().whenComplete((ignored, failure) ->
                Platform.runLater(() -> {
                    loadGitStatuses();
                    if (projectService != null) {
                        refreshProjects();
                    }
                })
        );
    }

    void setOnAddScanDirectory(Runnable action) {
        onAddScanDirectory = Objects.requireNonNull(action);
    }

    void setOnAddManualProject(Runnable action) {
        onAddManualProject = Objects.requireNonNull(action);
    }

    void setOnOpenSettings(Runnable action) {
        onOpenSettings = Objects.requireNonNull(action);
    }

    void setOnOpenProject(Consumer<Project> action) {
        onOpenProject = Objects.requireNonNull(action);
    }

    @FXML
    private void addScanDirectory() {
        Objects.requireNonNull(
                onAddScanDirectory,
                "Add scan directory callback not configured"
        ).run();
    }

    @FXML
    private void addManualProject() {
        Objects.requireNonNull(
                onAddManualProject,
                "Manual project callback not configured"
        ).run();
    }

    @FXML
    private void openSettings() {
        Objects.requireNonNull(onOpenSettings, "Settings callback not configured").run();
    }

    @FXML
    private void refreshProjects() {
        showProjects(searchField.getText());
    }

    private void showProjects(String query) {
        try {
            var projects = Objects.requireNonNull(
                    projectService,
                    "Project service not configured"
            ).searchProjects(query);
            var searching = !query.isBlank();
            summaryLabel.setText((searching ? "找到 " : "共 ") + projects.size() + " 个项目");
            cardsPane.getChildren().setAll(projects.stream().map(this::createCard).toList());
            projectTable.setItems(FXCollections.observableArrayList(projects));
            if (projects.isEmpty()) {
                var empty = new Label(searching
                        ? "没有匹配的项目。"
                        : "还没有导入项目，请先添加扫描目录。");
                empty.getStyleClass().add("empty-text");
                cardsPane.getChildren().setAll(empty);
            }
            statusLabel.setText("");
        } catch (SQLException exception) {
            statusLabel.setText("无法读取项目列表，请稍后重试。");
        }
    }

    @FXML private void showCardView() { setAndSaveView(ViewMode.CARD); }
    @FXML private void showTableView() { setAndSaveView(ViewMode.TABLE); }

    private void setAndSaveView(ViewMode mode) {
        setView(mode);
        if (settingsService != null) settingsService.setViewMode(mode);
    }

    private void setView(ViewMode mode) {
        var card = mode == ViewMode.CARD;
        cardViewButton.setSelected(card);
        tableViewButton.setSelected(!card);
        cardsScroll.setVisible(card);
        cardsScroll.setManaged(card);
        projectTable.setVisible(!card);
        projectTable.setManaged(!card);
    }

    private GitStatusCache cacheValue(Project project) {
        return gitStatuses.getOrDefault(project.id(), GitStatusCache.pending(project.id()));
    }

    private static ReadOnlyStringWrapper text(Object value) {
        return new ReadOnlyStringWrapper(value == null ? "—" : value.toString());
    }

    private StackPane createCard(Project project) {
        var title = new Label(project.displayName());
        title.getStyleClass().add("project-card-title");
        var spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        var pathState = new Label(pathStateText(project.pathStatus()));
        pathState.getStyleClass().add(project.pathStatus() == PathStatus.NORMAL
                ? "project-status-normal"
                : "project-status-warning");
        var heading = new HBox(10, title, spacer, pathState);
        heading.setAlignment(Pos.CENTER_LEFT);

        var path = new Label(project.path().toString());
        path.setWrapText(true);
        path.getStyleClass().add("project-card-path");

        var gitState = meta(gitStateText(gitStatuses.get(project.id())));
        var ide = meta("默认 IDE：" + (project.defaultIdeId() == null
                ? "未设置"
                : ideNames.getOrDefault(project.defaultIdeId(), "不可用")));
        var opened = meta(project.lastOpenedAt() == null
                ? "最近打开：从未打开"
                : "最近打开：" + DATE_TIME.format(project.lastOpenedAt()));

        var detail = new Label("查看详情 →");
        detail.getStyleClass().add("project-link");
        var footer = new HBox(detail);
        footer.setAlignment(Pos.CENTER_RIGHT);

        var content = new VBox(10, heading, path, gitState, ide, opened);
        content.setPrefWidth(364);
        if (project.nestedRepository()) {
            var nested = new Label("嵌套项目");
            nested.getStyleClass().add("nested-text");
            content.getChildren().add(nested);
        }
        content.getChildren().add(footer);
        var card = new Button();
        card.setGraphic(content);
        card.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        card.setAccessibleText("查看项目 " + project.displayName());
        card.setOnAction(event -> Objects.requireNonNull(
                onOpenProject,
                "Open project callback not configured"
        ).accept(project));
        card.getStyleClass().add("project-card");

        var launch = new Button("启动项目");
        launch.setDisable(!canLaunch(project, ideNames));
        launch.setOnAction(event -> launchProject(project));
        StackPane.setAlignment(launch, Pos.BOTTOM_LEFT);
        StackPane.setMargin(launch, new Insets(0, 0, 18, 18));
        return new StackPane(card, launch);
    }

    private void launchProject(Project project) {
        try {
            var ideId = Objects.requireNonNull(project.defaultIdeId());
            Objects.requireNonNull(ideService, "IdeService not configured")
                    .openProject(project, ideId);
            refreshProjects();
            statusLabel.setText("已使用 " + ideNames.get(ideId) + " 打开 " + project.displayName() + "。");
        } catch (IllegalArgumentException | SQLException | IOException exception) {
            statusLabel.setText("无法启动项目，请检查项目路径和默认 IDE。");
        }
    }

    static boolean canLaunch(Project project, Map<UUID, String> availableIdes) {
        return project.pathStatus() == PathStatus.NORMAL
                && project.defaultIdeId() != null
                && availableIdes.containsKey(project.defaultIdeId());
    }

    private static Label meta(String text) {
        var label = new Label(text);
        label.getStyleClass().add("project-meta");
        return label;
    }

    private void loadGitStatuses() {
        try {
            gitStatuses = gitStatusService.cachedStatuses();
        } catch (SQLException exception) {
            gitStatuses = Map.of();
        }
    }

    static String gitStateText(GitStatusCache cache) {
        if (cache == null || cache.refreshStatus() == GitStatusCache.RefreshStatus.PENDING) {
            return "Git 状态：待刷新";
        }
        if (cache.refreshStatus() == GitStatusCache.RefreshStatus.FAILED) {
            return "Git 状态：刷新失败";
        }
        if (cache.hasConflict()) {
            return "Git 状态：存在冲突 · " + cache.conflictFileCount() + " 个文件";
        }
        var branch = cache.currentBranch() == null ? "无提交" : cache.currentBranch();
        var remote = cache.aheadCount() == null
                ? ""
                : " · ↑" + cache.aheadCount() + " ↓" + cache.behindCount();
        return "Git 状态：" + branch + " · " + cache.uncommittedFileCount()
                + " 个文件未提交" + remote;
    }

    static String pathStateText(PathStatus status) {
        return switch (status) {
            case NORMAL -> "路径正常";
            case UNAVAILABLE -> "路径失效";
            case INACCESSIBLE -> "路径不可访问";
        };
    }
}
