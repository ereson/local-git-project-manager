package com.localprojectmanager.ui;

import com.localprojectmanager.application.ide.IdeService;
import com.localprojectmanager.application.git.GitStatusService;
import com.localprojectmanager.application.git.GitOperationService;
import com.localprojectmanager.application.project.ProjectImportService;
import com.localprojectmanager.application.project.ProjectLaunchService;
import com.localprojectmanager.domain.git.GitOperationRecord;
import com.localprojectmanager.domain.git.GitStatusCache;
import com.localprojectmanager.domain.ide.IdeConfig;
import com.localprojectmanager.domain.project.PathStatus;
import com.localprojectmanager.domain.project.Project;
import com.localprojectmanager.domain.project.PullStrategy;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TextInputDialog;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.IOException;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.List;
import java.util.Locale;

public final class ProjectDetailViewController {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    @FXML
    private Label displayNameLabel;

    @FXML
    private Label directoryNameLabel;

    @FXML
    private Label pathLabel;

    @FXML
    private Label pathStatusLabel;

    @FXML
    private ComboBox<IdeConfig> defaultIdeChoice;

    @FXML
    private Button openProjectButton;

    @FXML
    private Button clearDefaultIdeButton;

    @FXML
    private Button openExplorerButton;

    @FXML
    private Button openTerminalButton;

    @FXML
    private Label ideStatusLabel;

    @FXML
    private Button refreshGitButton;

    @FXML
    private Button fetchButton;

    @FXML
    private Button pullButton;

    @FXML
    private ComboBox<PullStrategy> pullStrategyChoice;

    @FXML
    private Label currentBranchLabel;

    @FXML
    private Label modificationsLabel;

    @FXML
    private Label latestCommitLabel;

    @FXML
    private Label remoteUrlLabel;

    @FXML
    private Label gitStatusLabel;

    @FXML
    private TextField branchSearchField;

    @FXML
    private ListView<String> localBranchesList;

    @FXML
    private ListView<String> remoteBranchesList;

    @FXML
    private Label branchStatusLabel;

    @FXML
    private Label lastOperationLabel;

    @FXML
    private Button switchLocalBranchButton;

    @FXML
    private Button trackRemoteBranchButton;

    private Runnable onBack;
    private IdeService ideService;
    private GitStatusService gitStatusService;
    private GitOperationService gitOperationService;
    private ProjectImportService projectService;
    private ProjectLaunchService projectLaunchService;
    private GitStatusCache currentGitStatus;
    private Project project;
    private List<String> localBranches = List.of();
    private List<String> remoteBranches = List.of();

    @FXML
    private void initialize() {
        defaultIdeChoice.valueProperty().addListener((ignored, oldValue, newValue) ->
                updateOpenButton()
        );
        branchSearchField.textProperty().addListener((ignored, oldValue, newValue) ->
                filterBranches(newValue)
        );
        localBranchesList.getSelectionModel().selectedItemProperty().addListener(
                (ignored, oldValue, newValue) -> switchLocalBranchButton.setDisable(newValue == null)
        );
        remoteBranchesList.getSelectionModel().selectedItemProperty().addListener(
                (ignored, oldValue, newValue) -> trackRemoteBranchButton.setDisable(newValue == null)
        );
    }

    void configure(
            Project project,
            IdeService ideService,
            GitStatusService gitStatusService,
            GitOperationService gitOperationService,
            ProjectImportService projectService,
            ProjectLaunchService projectLaunchService
    ) {
        this.project = Objects.requireNonNull(project);
        this.ideService = Objects.requireNonNull(ideService);
        this.gitStatusService = Objects.requireNonNull(gitStatusService);
        this.gitOperationService = Objects.requireNonNull(gitOperationService);
        this.projectService = Objects.requireNonNull(projectService);
        this.projectLaunchService = Objects.requireNonNull(projectLaunchService);
        displayNameLabel.setText(project.displayName());
        directoryNameLabel.setText(project.directoryName());
        pathLabel.setText(project.path().toString());
        pathStatusLabel.setText(ProjectHomeViewController.pathStateText(project.pathStatus()));
        pathStatusLabel.getStyleClass().add(project.pathStatus() == PathStatus.NORMAL
                ? "project-status-normal"
                : "project-status-warning");
        openExplorerButton.setDisable(project.pathStatus() != PathStatus.NORMAL);
        openTerminalButton.setDisable(project.pathStatus() != PathStatus.NORMAL);
        pullStrategyChoice.setItems(FXCollections.observableArrayList(PullStrategy.values()));
        pullStrategyChoice.getSelectionModel().select(
                project.pullStrategy() == null ? PullStrategy.REBASE : project.pullStrategy()
        );
        try {
            var available = ideService.listAvailableIdes();
            defaultIdeChoice.setItems(FXCollections.observableArrayList(available));
            available.stream()
                    .filter(ide -> ide.id().equals(project.defaultIdeId()))
                    .findFirst()
                    .ifPresent(defaultIdeChoice.getSelectionModel()::select);
            clearDefaultIdeButton.setDisable(project.defaultIdeId() == null);
            updateOpenButton();
            ideStatusLabel.setText(available.isEmpty()
                    ? "未检测到可用 IDE。"
                    : project.defaultIdeId() != null && defaultIdeChoice.getValue() == null
                            ? "默认 IDE 当前不可用，请重新选择或清除。"
                            : "");
        } catch (SQLException exception) {
            defaultIdeChoice.setDisable(true);
            openProjectButton.setDisable(true);
            clearDefaultIdeButton.setDisable(true);
            ideStatusLabel.setText("无法读取 IDE 配置，请稍后重试。");
        }
        try {
            showGitStatus(gitStatusService.cachedStatuses().get(project.id()));
        } catch (SQLException exception) {
            showGitStatus(null);
        }
        refreshGitButton.setDisable(project.pathStatus() != PathStatus.NORMAL);
        fetchButton.setDisable(project.pathStatus() != PathStatus.NORMAL);
        pullButton.setDisable(project.pathStatus() != PathStatus.NORMAL);
        if (!refreshGitButton.isDisable()) {
            refreshGitStatus();
            loadBranches();
        }
        showLastOperation();
    }

    @FXML
    private void savePullStrategy() {
        try {
            project = projectService.setPullStrategy(project, pullStrategyChoice.getValue());
            gitStatusLabel.setText("已保存项目 Pull 策略。");
        } catch (SQLException exception) {
            gitStatusLabel.setText("Pull 策略保存失败。");
        }
    }

    @FXML
    private void pullProject() {
        if (currentGitStatus == null) {
            gitStatusLabel.setText("请先刷新 Git 状态。");
            return;
        }
        var selected = pullStrategyChoice.getValue();
        var dialog = new ChoiceDialog<>(selected, PullStrategy.values());
        dialog.setTitle("确认 Pull");
        dialog.setHeaderText(project.displayName() + " · "
                + value(currentGitStatus.currentBranch(), "未知分支"));
        dialog.setContentText("上游：" + value(currentGitStatus.upstreamBranch(), "未设置")
                + "\n未提交：" + currentGitStatus.uncommittedFileCount()
                + "\nAhead/Behind：" + value(currentGitStatus.aheadCount(), "未知")
                + "/" + value(currentGitStatus.behindCount(), "未知")
                + "\n策略：");
        DialogTheme.apply(dialog);
        dialog.showAndWait().ifPresent(this::runPull);
    }

    private void runPull(PullStrategy strategy) {
        pullButton.setDisable(true);
        gitStatusLabel.setText("正在执行 Pull…");
        gitOperationService.pull(project, strategy).whenComplete((outcome, failure) ->
                Platform.runLater(() -> {
                    pullButton.setDisable(project.pathStatus() != PathStatus.NORMAL);
                    gitStatusLabel.setText(failure == null
                            ? outcome.message()
                            : "Pull 失败，请稍后重试。");
                    if (failure == null && outcome.successful()) {
                        refreshGitStatus();
                        loadBranches();
                    }
                    showLastOperation();
                })
        );
    }

    private void loadBranches() {
        branchStatusLabel.setText("正在读取分支…");
        gitOperationService.listBranches(project).whenComplete((branches, failure) ->
                Platform.runLater(() -> {
                    if (failure != null) {
                        branchStatusLabel.setText("无法读取分支列表。");
                        return;
                    }
                    localBranches = branches.local();
                    remoteBranches = branches.remote();
                    filterBranches(branchSearchField.getText());
                    branchStatusLabel.setText("本地 " + localBranches.size()
                            + " 个 · 远程 " + remoteBranches.size() + " 个");
                })
        );
    }

    private void filterBranches(String query) {
        var normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        localBranchesList.setItems(FXCollections.observableArrayList(localBranches.stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).contains(normalized))
                .toList()));
        remoteBranchesList.setItems(FXCollections.observableArrayList(remoteBranches.stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).contains(normalized))
                .toList()));
    }

    @FXML
    private void switchLocalBranch() {
        var branch = localBranchesList.getSelectionModel().getSelectedItem();
        if (branch != null) {
            runBranchSwitch(branch, false, false);
        }
    }

    @FXML
    private void trackRemoteBranch() {
        var branch = remoteBranchesList.getSelectionModel().getSelectedItem();
        if (branch != null) {
            runBranchSwitch(branch, true, false);
        }
    }

    private void runBranchSwitch(String branch, boolean remote, boolean allowDirty) {
        switchLocalBranchButton.setDisable(true);
        trackRemoteBranchButton.setDisable(true);
        branchStatusLabel.setText("正在切换分支…");
        var operation = remote
                ? gitOperationService.createRemoteTrackingBranch(project, branch, allowDirty)
                : gitOperationService.switchLocalBranch(project, branch, allowDirty);
        operation.whenComplete((outcome, failure) -> Platform.runLater(() -> {
            if (failure != null) {
                branchStatusLabel.setText("分支切换失败，请稍后重试。");
            } else if (outcome.confirmationRequired()) {
                var alert = new Alert(
                        Alert.AlertType.CONFIRMATION,
                        outcome.message() + "\n软件不会自动 Stash，是否仍要继续？",
                        ButtonType.OK,
                        ButtonType.CANCEL
                );
                alert.setTitle("确认切换分支");
                alert.setHeaderText("存在未提交修改");
                DialogTheme.apply(alert);
                if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                    runBranchSwitch(branch, remote, true);
                    return;
                }
                branchStatusLabel.setText("已取消分支切换。");
            } else {
                branchStatusLabel.setText(outcome.message());
                if (outcome.successful()) {
                    refreshGitStatus();
                    loadBranches();
                }
            }
            showLastOperation();
            switchLocalBranchButton.setDisable(
                    localBranchesList.getSelectionModel().getSelectedItem() == null
            );
            trackRemoteBranchButton.setDisable(
                    remoteBranchesList.getSelectionModel().getSelectedItem() == null
            );
        }));
    }

    private void showLastOperation() {
        try {
            var operation = gitOperationService.lastOperation(project.id());
            lastOperationLabel.setText(operation.map(ProjectDetailViewController::operationText)
                    .orElse("最近操作：无"));
            setOperationStyle(operation.map(GitOperationRecord::status).orElse(null));
        } catch (SQLException exception) {
            lastOperationLabel.setText("最近操作：读取失败");
            setOperationStyle(GitOperationRecord.Status.FAILED);
        }
    }

    static String operationText(GitOperationRecord operation) {
        var type = switch (operation.type()) {
            case FETCH -> "Fetch";
            case PULL -> "Pull";
            case SWITCH_BRANCH -> "切换分支";
        };
        var status = switch (operation.status()) {
            case SUCCESS -> "成功";
            case FAILED -> "失败";
            case CONFLICT -> "存在冲突";
        };
        return "最近操作：" + type + " · " + status + " · " + DATE_TIME.format(operation.finishedAt());
    }

    private void setOperationStyle(GitOperationRecord.Status status) {
        lastOperationLabel.getStyleClass().removeAll(
                "operation-status-success", "operation-status-failed", "operation-status-conflict"
        );
        if (status != null) {
            lastOperationLabel.getStyleClass().add(switch (status) {
                case SUCCESS -> "operation-status-success";
                case FAILED -> "operation-status-failed";
                case CONFLICT -> "operation-status-conflict";
            });
        }
    }

    @FXML
    private void fetchRemote() {
        fetchButton.setDisable(true);
        gitStatusLabel.setText("正在检查远程更新…");
        gitOperationService.fetch(project).whenComplete((outcome, failure) ->
                Platform.runLater(() -> {
                    fetchButton.setDisable(project.pathStatus() != PathStatus.NORMAL);
                    if (failure != null) {
                        gitStatusLabel.setText("检查远程更新失败，请稍后重试。");
                    } else {
                        gitStatusLabel.setText(outcome.message());
                        if (outcome.successful()) {
                            refreshGitStatus();
                        }
                    }
                })
        );
    }

    @FXML
    private void refreshGitStatus() {
        refreshGitButton.setDisable(true);
        gitStatusLabel.setText("正在刷新 Git 状态…");
        gitStatusService.refresh(project).whenComplete((cache, failure) ->
                Platform.runLater(() -> {
                    refreshGitButton.setDisable(project.pathStatus() != PathStatus.NORMAL);
                    if (failure == null) {
                        showGitStatus(cache);
                    } else {
                        gitStatusLabel.setText("Git 状态刷新失败，请稍后重试。");
                    }
                })
        );
    }

    private void showGitStatus(GitStatusCache cache) {
        currentGitStatus = cache;
        if (cache == null) {
            currentBranchLabel.setText("待刷新");
            modificationsLabel.setText("待刷新");
            latestCommitLabel.setText("待刷新");
            remoteUrlLabel.setText("待刷新");
            gitStatusLabel.setText("尚无 Git 状态缓存。");
            return;
        }
        currentBranchLabel.setText(cache.currentBranch() == null ? "无提交" : cache.currentBranch());
        modificationsLabel.setText(cache.uncommittedFileCount() + " 个文件");
        latestCommitLabel.setText(cache.latestCommitHash() == null
                ? "无提交"
                : cache.latestCommitHash().substring(0, Math.min(7, cache.latestCommitHash().length()))
                        + " · " + cache.latestCommitMessage());
        remoteUrlLabel.setText(cache.remoteUrl() == null ? "未配置" : cache.remoteUrl());
        gitStatusLabel.setText(cache.refreshStatus() == GitStatusCache.RefreshStatus.FAILED
                ? "刷新失败：" + cache.refreshError()
                : cache.hasConflict()
                        ? "存在冲突：" + cache.conflictFileCount()
                                + " 个文件，请使用 IDE 或终端处理后刷新。"
                        : "更新于 " + DATE_TIME.format(cache.localStatusUpdatedAt()));
    }

    private static String value(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    @FXML
    private void saveDefaultIde() {
        var selected = defaultIdeChoice.getValue();
        if (selected == null) {
            ideStatusLabel.setText("请先选择一个 IDE。");
            return;
        }
        try {
            project = ideService.setDefaultIde(project, selected.id());
            clearDefaultIdeButton.setDisable(false);
            updateOpenButton();
            ideStatusLabel.setText("已将 " + selected + " 设为默认 IDE。");
        } catch (SQLException | IllegalArgumentException exception) {
            ideStatusLabel.setText("默认 IDE 保存失败，请重新检测后再试。");
        }
    }

    @FXML
    private void clearDefaultIde() {
        try {
            project = ideService.clearDefaultIde(project);
            defaultIdeChoice.getSelectionModel().clearSelection();
            clearDefaultIdeButton.setDisable(true);
            updateOpenButton();
            ideStatusLabel.setText("已清除默认 IDE。");
        } catch (SQLException exception) {
            ideStatusLabel.setText("默认 IDE 清除失败，请稍后重试。");
        }
    }

    @FXML
    private void openProject() {
        var selected = defaultIdeChoice.getValue();
        if (selected == null) {
            ideStatusLabel.setText("请先选择一个 IDE。");
            return;
        }
        try {
            project = ideService.openProject(project, selected.id());
            clearDefaultIdeButton.setDisable(false);
            ideStatusLabel.setText("已使用 " + selected + " 打开项目。");
        } catch (IOException exception) {
            ideStatusLabel.setText("IDE 启动失败，请检查可执行文件是否仍然可用。");
        } catch (SQLException exception) {
            ideStatusLabel.setText("无法读取或保存 IDE 配置，请稍后重试。");
        } catch (IllegalArgumentException exception) {
            ideStatusLabel.setText("项目路径或 IDE 当前不可用。");
        }
    }

    @FXML
    private void openWithOtherIde() {
        var chooser = new FileChooser();
        chooser.setTitle("选择 IDE 可执行文件");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Windows 程序", "*.exe"));
        var executable = chooser.showOpenDialog(pathLabel.getScene().getWindow());
        if (executable == null) {
            return;
        }
        try {
            var name = executable.getName().replaceFirst("(?i)\\.exe$", "");
            var ide = ideService.addManualIde(name, executable.toPath());
            project = ideService.openProject(project, ide.id(), false);
            if (!defaultIdeChoice.getItems().contains(ide)) {
                defaultIdeChoice.getItems().add(ide);
            }
            ideStatusLabel.setText("已使用 " + ide + " 打开，本次未更改默认 IDE。");
        } catch (IOException exception) {
            ideStatusLabel.setText("IDE 启动失败，请检查该程序是否可以运行。");
        } catch (SQLException exception) {
            ideStatusLabel.setText("手动 IDE 保存失败，请稍后重试。");
        } catch (IllegalArgumentException exception) {
            ideStatusLabel.setText("所选文件不是可用的 IDE 程序。");
        }
    }

    @FXML
    private void openInExplorer() {
        launchProjectLocation(true);
    }

    @FXML
    private void openInTerminal() {
        launchProjectLocation(false);
    }

    private void launchProjectLocation(boolean explorer) {
        try {
            if (explorer) {
                projectLaunchService.openInExplorer(project.path());
            } else {
                projectLaunchService.openInTerminal(project.path());
            }
            ideStatusLabel.setText(explorer ? "已在资源管理器中打开。" : "已在终端中打开。");
        } catch (IOException | IllegalArgumentException exception) {
            ideStatusLabel.setText("项目路径当前不可用，无法打开。");
        }
    }

    @FXML
    private void renameProject() {
        var dialog = new TextInputDialog(project.displayName());
        dialog.setTitle("重命名项目");
        dialog.setHeaderText("修改项目显示名称");
        dialog.setContentText("名称：");
        DialogTheme.apply(dialog);
        dialog.showAndWait().ifPresent(name -> {
            try {
                project = projectService.rename(project, name);
                displayNameLabel.setText(project.displayName());
                ideStatusLabel.setText("项目名称已保存。");
            } catch (SQLException | IllegalArgumentException exception) {
                ideStatusLabel.setText("项目名称保存失败。");
            }
        });
    }

    @FXML
    private void relocateProject() {
        var chooser = new DirectoryChooser();
        chooser.setTitle("重新指定 Git 项目目录");
        var directory = chooser.showDialog(pathLabel.getScene().getWindow());
        if (directory == null) {
            return;
        }
        try {
            project = projectService.relocate(project, directory.toPath());
            pathLabel.setText(project.path().toString());
            pathStatusLabel.setText(ProjectHomeViewController.pathStateText(project.pathStatus()));
            openExplorerButton.setDisable(false);
            openTerminalButton.setDisable(false);
            refreshGitButton.setDisable(false);
            fetchButton.setDisable(false);
            pullButton.setDisable(false);
            updateOpenButton();
            ideStatusLabel.setText("项目路径已更新。");
            refreshGitStatus();
        } catch (SQLException | IllegalArgumentException exception) {
            ideStatusLabel.setText("所选目录无效或已经被其他项目使用。");
        }
    }

    @FXML
    private void removeProject() {
        var alert = new Alert(
                Alert.AlertType.CONFIRMATION,
                "只会移除管理记录，不会删除本地项目文件。",
                ButtonType.OK,
                ButtonType.CANCEL
        );
        alert.setTitle("从软件中移除");
        alert.setHeaderText("确认移除 " + project.displayName() + "？");
        DialogTheme.apply(alert);
        if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            if (projectService.remove(project)) {
                goBack();
            }
        } catch (SQLException exception) {
            ideStatusLabel.setText("移除项目记录失败。");
        }
    }

    private void updateOpenButton() {
        openProjectButton.setDisable(project == null
                || project.pathStatus() != PathStatus.NORMAL
                || defaultIdeChoice.getValue() == null);
    }

    void setOnBack(Runnable action) {
        onBack = Objects.requireNonNull(action);
    }

    @FXML
    private void goBack() {
        Objects.requireNonNull(onBack, "Navigation callback not configured").run();
    }
}
