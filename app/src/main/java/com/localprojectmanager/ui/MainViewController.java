package com.localprojectmanager.ui;

import com.localprojectmanager.application.ide.IdeService;
import com.localprojectmanager.application.git.GitStatusService;
import com.localprojectmanager.application.git.GitOperationService;
import com.localprojectmanager.application.project.ProjectImportService;
import com.localprojectmanager.application.project.ProjectLaunchService;
import com.localprojectmanager.application.scan.GitRepositoryScanner.ScanResult;
import com.localprojectmanager.application.scan.ScanRootService;
import com.localprojectmanager.application.settings.ApplicationSettingsService;
import com.localprojectmanager.application.settings.ApplicationSettingsService.ThemeMode;
import com.localprojectmanager.domain.project.Project;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Consumer;
import java.nio.file.Path;
import com.localprojectmanager.application.update.UpdateService;
import com.localprojectmanager.domain.update.UpdatePackageType;
import com.localprojectmanager.domain.update.UpdateCheckResult;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import java.util.concurrent.CompletableFuture;

public final class MainViewController {

    @FXML
    private StackPane pageHost;

    private ScanRootService scanRootService;
    private ProjectImportService projectImportService;
    private IdeService ideService;
    private GitStatusService gitStatusService;
    private GitOperationService gitOperationService;
    private ProjectLaunchService projectLaunchService;
    private ApplicationSettingsService settingsService;
    private Consumer<ThemeMode> onThemeChanged;
    private UpdateService updateService;
    private UpdatePackageType updatePackageType;
    private Consumer<Path> onLaunchUpdate;
    private Runnable onOpenLogs;

    private void showWelcome() {
        var loader = loadPage("/fxml/welcome-view.fxml");
        WelcomeViewController controller = loader.getController();
        controller.setOnChooseScanDirectory(() -> showScanRoots(this::showWelcome));
        controller.setOnAddManualProject(() -> chooseManualProject(this::showWelcome));
    }

    private void showScanRoots(Runnable onBack) {
        var loader = loadPage("/fxml/scan-roots-view.fxml");
        ScanRootsViewController controller = loader.getController();
        controller.setOnBack(onBack);
        controller.setOnScanCompleted(result -> showScanResults(result, onBack));
        controller.setViewModel(new ScanRootsViewModel(
                Objects.requireNonNull(scanRootService, "ScanRootService not configured"),
                settingsService
        ));
    }

    private void showScanResults(ScanResult result, Runnable onBack) {
        var loader = loadPage("/fxml/scan-results-view.fxml");
        ScanResultsViewController controller = loader.getController();
        controller.setOnBack(() -> showScanRoots(onBack));
        controller.setOnImportCompleted(this::showProjectHome);
        controller.setImportService(Objects.requireNonNull(
                projectImportService,
                "ProjectImportService not configured"
        ));
        controller.setViewModel(new ScanResultsViewModel(result));
    }

    private void showProjectHome() {
        var loader = loadPage("/fxml/project-home-view.fxml");
        ProjectHomeViewController controller = loader.getController();
        controller.setOnAddScanDirectory(() -> showScanRoots(this::showProjectHome));
        controller.setOnAddManualProject(() -> chooseManualProject(this::showProjectHome));
        controller.setOnOpenSettings(this::showSettings);
        controller.setOnOpenProject(this::showProjectDetail);
        controller.setSettingsService(Objects.requireNonNull(settingsService));
        controller.setIdeService(Objects.requireNonNull(ideService, "IdeService not configured"));
        controller.setGitStatusService(Objects.requireNonNull(
                gitStatusService,
                "GitStatusService not configured"
        ));
        controller.setProjectService(Objects.requireNonNull(
                projectImportService,
                "ProjectImportService not configured"
        ));
    }

    private void showProjectDetail(Project project) {
        var loader = loadPage("/fxml/project-detail-view.fxml");
        ProjectDetailViewController controller = loader.getController();
        controller.setOnBack(this::showProjectHome);
        controller.configure(
                project,
                Objects.requireNonNull(ideService, "IdeService not configured"),
                Objects.requireNonNull(gitStatusService, "GitStatusService not configured"),
                Objects.requireNonNull(gitOperationService, "GitOperationService not configured"),
                Objects.requireNonNull(projectImportService, "ProjectImportService not configured"),
                Objects.requireNonNull(projectLaunchService, "ProjectLaunchService not configured")
        );
    }

    private void showSettings() {
        var loader = loadPage("/fxml/settings-view.fxml");
        SettingsViewController controller = loader.getController();
        controller.configure(
                Objects.requireNonNull(settingsService, "SettingsService not configured"),
                Objects.requireNonNull(onThemeChanged, "Theme callback not configured"),
                () -> checkForUpdates(false),
                Objects.requireNonNull(onOpenLogs, "Open logs callback not configured"),
                this::showProjectHome
        );
    }

    public void showSettingsPage() {
        showSettings();
    }

    public void showProjectPage(Project project) {
        showProjectDetail(project);
    }

    public void showProjectHomePage() {
        showProjectHome();
    }

    private void chooseManualProject(Runnable onBack) {
        var chooser = new DirectoryChooser();
        chooser.setTitle("选择 Git 项目目录");
        var directory = chooser.showDialog(pageHost.getScene().getWindow());
        if (directory == null) {
            return;
        }
        try {
            var repository = Objects.requireNonNull(projectImportService)
                    .inspectManualProject(directory.toPath());
            showScanResults(new ScanResult(java.util.List.of(repository), java.util.List.of(), false), onBack);
        } catch (IllegalArgumentException exception) {
            var alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("无法添加项目");
            alert.setHeaderText("所选目录不是有效的 Git 项目");
            alert.setContentText("请选择包含 .git 目录或 .git 文件的项目根目录。");
            DialogTheme.apply(alert);
            alert.showAndWait();
        }
    }

    public void showInitialPage() {
        try {
            if (Objects.requireNonNull(
                    projectImportService,
                    "ProjectImportService not configured"
            ).listProjects().isEmpty()) {
                showWelcome();
            } else {
                showProjectHome();
            }
        } catch (SQLException exception) {
            showWelcome();
        }
    }

    public void setScanRootService(ScanRootService scanRootService) {
        this.scanRootService = Objects.requireNonNull(scanRootService);
    }

    public void setProjectImportService(ProjectImportService projectImportService) {
        this.projectImportService = Objects.requireNonNull(projectImportService);
    }

    public void setIdeService(IdeService ideService) {
        this.ideService = Objects.requireNonNull(ideService);
    }

    public void setGitStatusService(GitStatusService gitStatusService) {
        this.gitStatusService = Objects.requireNonNull(gitStatusService);
    }

    public void setGitOperationService(GitOperationService gitOperationService) {
        this.gitOperationService = Objects.requireNonNull(gitOperationService);
    }

    public void setProjectLaunchService(ProjectLaunchService projectLaunchService) {
        this.projectLaunchService = Objects.requireNonNull(projectLaunchService);
    }

    public void setSettingsService(
            ApplicationSettingsService settingsService,
            Consumer<ThemeMode> onThemeChanged
    ) {
        this.settingsService = Objects.requireNonNull(settingsService);
        this.onThemeChanged = Objects.requireNonNull(onThemeChanged);
    }

    public void setUpdateService(
            UpdateService updateService,
            UpdatePackageType packageType,
            Consumer<Path> onLaunchUpdate
    ) {
        this.updateService = updateService;
        this.updatePackageType = Objects.requireNonNull(packageType);
        this.onLaunchUpdate = Objects.requireNonNull(onLaunchUpdate);
    }

    public void setOnOpenLogs(Runnable action) {
        onOpenLogs = Objects.requireNonNull(action);
    }

    public void checkUpdatesOnStartup() {
        if (settingsService.checkUpdatesOnStartup()) {
            checkForUpdates(true);
        }
    }

    private void checkForUpdates(boolean silent) {
        if (updateService == null) {
            if (!silent) showMessage("软件更新", "尚未配置更新服务地址。");
            return;
        }
        CompletableFuture.supplyAsync(updateService::check).whenComplete((result, failure) ->
                Platform.runLater(() -> {
                    if (failure != null || result.status() == UpdateCheckResult.Status.FAILED) {
                        if (!silent) showMessage("软件更新", "检查更新失败，不影响继续使用。");
                        return;
                    }
                    if (result.status() == UpdateCheckResult.Status.CURRENT) {
                        if (!silent) showMessage("软件更新", result.message());
                        return;
                    }
                    confirmDownload(result);
                })
        );
    }

    private void confirmDownload(UpdateCheckResult result) {
        var download = new ButtonType("下载并安装");
        var alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("发现新版本");
        alert.setHeaderText("可更新到 " + result.update().version());
        alert.setContentText("发布时间：" + result.update().publishedAt()
                + "\n\n" + String.join("\n", result.update().releaseNotes()));
        alert.getButtonTypes().setAll(download, ButtonType.CANCEL);
        DialogTheme.apply(alert);
        if (alert.showAndWait().orElse(ButtonType.CANCEL) != download) {
            return;
        }
        CompletableFuture.supplyAsync(() -> updateService.download(result.update(), updatePackageType))
                .whenComplete((downloadResult, failure) -> Platform.runLater(() -> {
                    if (failure != null || !downloadResult.successful()) {
                        showMessage("软件更新", "更新包下载或校验失败。");
                    } else {
                        showMessage("软件更新", downloadResult.message() + " 即将打开更新包。");
                        onLaunchUpdate.accept(downloadResult.file());
                    }
                }));
    }

    private static void showMessage(String title, String text) {
        var alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(text);
        DialogTheme.apply(alert);
        alert.showAndWait();
    }

    private FXMLLoader loadPage(String resourcePath) {
        var resource = Objects.requireNonNull(
                getClass().getResource(resourcePath),
                "Missing " + resourcePath
        );
        var loader = new FXMLLoader(resource);
        try {
            Node page = loader.load();
            pageHost.getChildren().setAll(page);
            return loader;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load " + resourcePath, exception);
        }
    }
}
