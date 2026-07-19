package com.localprojectmanager.bootstrap;

import com.localprojectmanager.application.ide.IdeService;
import com.localprojectmanager.application.git.GitStatusService;
import com.localprojectmanager.application.git.GitOperationService;
import com.localprojectmanager.application.git.ProjectGitLock;
import com.localprojectmanager.application.project.ProjectImportService;
import com.localprojectmanager.application.project.ProjectLaunchService;
import com.localprojectmanager.application.scan.ScanRootService;
import com.localprojectmanager.application.settings.ApplicationSettingsService;
import com.localprojectmanager.application.settings.ApplicationSettingsService.ThemeMode;
import com.localprojectmanager.infrastructure.database.Database;
import com.localprojectmanager.infrastructure.database.IdeConfigRepository;
import com.localprojectmanager.infrastructure.database.GitStatusCacheRepository;
import com.localprojectmanager.infrastructure.database.GitOperationRepository;
import com.localprojectmanager.infrastructure.database.ProjectRepository;
import com.localprojectmanager.infrastructure.database.ScanRootRepository;
import com.localprojectmanager.infrastructure.database.ApplicationSettingsRepository;
import com.localprojectmanager.infrastructure.ide.JetBrainsIdeDetector;
import com.localprojectmanager.infrastructure.ide.VisualStudioCodeDetector;
import com.localprojectmanager.infrastructure.git.GitClient;
import com.localprojectmanager.infrastructure.windows.WindowsProjectLauncher;
import com.localprojectmanager.ui.DialogTheme;
import com.localprojectmanager.ui.MainViewController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.application.ColorScheme;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.scene.control.ButtonType;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.awt.AWTException;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import org.slf4j.LoggerFactory;
import com.localprojectmanager.application.update.UpdateService;
import com.localprojectmanager.domain.update.UpdatePackageType;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.awt.Desktop;

public final class LocalProjectManagerApplication extends Application {

    private org.slf4j.Logger log;

    private Exception databaseFailure;
    private ScanRootService scanRootService;
    private ProjectImportService projectImportService;
    private IdeService ideService;
    private GitStatusService gitStatusService;
    private GitOperationService gitOperationService;
    private ProjectLaunchService projectLaunchService;
    private ApplicationSettingsService settingsService;
    private Scene scene;
    private Stage stage;
    private MainViewController mainController;
    private TrayIcon trayIcon;
    private boolean exiting;
    private boolean portable;
    private Path logsDirectory;

    @Override
    public void init() {
        try {
            portable = getParameters().getRaw().contains("--portable");
            var applicationDirectory = Database.applicationDirectory(portable);
            logsDirectory = applicationDirectory.resolve("logs");
            System.setProperty("lpm.logs.dir", logsDirectory.toString());
            System.setProperty("lpm.updates.dir", applicationDirectory.resolve("updates").toString());
            log = LoggerFactory.getLogger(LocalProjectManagerApplication.class);
            var database = Database.forRuntime(portable);
            Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
                    log.error("Unhandled application error on {}", thread.getName(), error));
            database.initialize();
            var scanRoots = new ScanRootRepository(database);
            var projects = new ProjectRepository(database);
            settingsService = new ApplicationSettingsService(new ApplicationSettingsRepository(database));
            scanRootService = new ScanRootService(scanRoots);
            projectImportService = new ProjectImportService(
                    projects, scanRoots, settingsService::globalPullStrategy
            );
            projectLaunchService = new ProjectLaunchService(new WindowsProjectLauncher());
            var git = new GitClient();
            var gitLocks = new ProjectGitLock();
            var gitCaches = new GitStatusCacheRepository(database);
            gitStatusService = new GitStatusService(git, projects, gitCaches, gitLocks);
            gitOperationService = new GitOperationService(
                    git, gitLocks, gitCaches, new GitOperationRepository(database)
            );
            ideService = new IdeService(
                    new IdeConfigRepository(database),
                    projects,
                    List.of(new VisualStudioCodeDetector(), new JetBrainsIdeDetector())
            );
            ideService.refreshDetectedIdes();
        } catch (Exception exception) {
            if (log != null) {
                log.error("Application initialization failed", exception);
            }
            databaseFailure = exception;
        }
    }

    @Override
    public void start(Stage stage) throws IOException {
        var stylesheet = Objects.requireNonNull(
                getClass().getResource("/css/application.css"),
                "Missing application.css"
        );
        DialogTheme.setStylesheets(List.of(stylesheet.toExternalForm()));
        if (databaseFailure != null) {
            var alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("本地 Git 项目管理器");
            alert.setHeaderText("数据库升级失败");
            alert.setContentText("无法打开或升级本地数据库，原数据库文件已保留。");
            DialogTheme.apply(alert);
            alert.showAndWait();
            Platform.exit();
            return;
        }

        var view = Objects.requireNonNull(
                getClass().getResource("/fxml/main-view.fxml"),
                "Missing main-view.fxml"
        );
        var loader = new FXMLLoader(view);
        scene = new Scene(loader.load(), 960, 640);
        MainViewController controller = loader.getController();
        mainController = controller;
        controller.setScanRootService(scanRootService);
        controller.setProjectImportService(projectImportService);
        controller.setIdeService(ideService);
        controller.setGitStatusService(gitStatusService);
        controller.setGitOperationService(gitOperationService);
        controller.setProjectLaunchService(projectLaunchService);
        controller.setSettingsService(settingsService, this::applyTheme);
        var updateUrl = System.getProperty("lpm.update.url", "");
        UpdateService updateService = updateUrl.isBlank() ? null : new UpdateService(
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build(),
                URI.create(updateUrl),
                "0.1.0",
                Path.of(System.getProperty("lpm.updates.dir"))
        );
        controller.setUpdateService(
                updateService,
                portable ? UpdatePackageType.PORTABLE : UpdatePackageType.INSTALLER,
                this::launchUpdate
        );
        controller.setOnOpenLogs(this::openLogsDirectory);
        scene.getStylesheets().add(stylesheet.toExternalForm());
        applyTheme(settingsService.theme());
        Platform.getPreferences().colorSchemeProperty().addListener(
                (ignored, oldValue, newValue) -> {
                    if (settingsService.theme() == ThemeMode.SYSTEM) {
                        applyTheme(ThemeMode.SYSTEM);
                    }
                }
        );
        controller.showInitialPage();

        stage.setTitle("本地 Git 项目管理器");
        stage.setMinWidth(800);
        stage.setMinHeight(520);
        stage.setScene(scene);
        this.stage = stage;
        stage.getIcons().add(createFxIcon(64));
        installTray();
        stage.setOnCloseRequest(this::handleCloseRequest);
        stage.show();
        controller.checkUpdatesOnStartup();
    }

    private void launchUpdate(Path updateFile) {
        try {
            Desktop.getDesktop().open(updateFile.toFile());
            if (!portable && updateFile.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".exe")) {
                exiting = true;
                Platform.exit();
            }
        } catch (IOException exception) {
            log.error("Unable to launch downloaded update package", exception);
        }
    }

    private void openLogsDirectory() {
        try {
            java.nio.file.Files.createDirectories(logsDirectory);
            Desktop.getDesktop().open(logsDirectory.toFile());
        } catch (IOException exception) {
            log.error("Unable to open logs directory", exception);
        }
    }

    private void installTray() {
        if (!SystemTray.isSupported()) {
            return;
        }
        var menu = new PopupMenu();
        menu.add(menuItem("打开主窗口", this::showMainWindow));
        menu.addSeparator();
        try {
            var recent = projectImportService.listProjects().stream().limit(5).toList();
            if (recent.isEmpty()) {
                var empty = new MenuItem("最近项目：无");
                empty.setEnabled(false);
                menu.add(empty);
            } else {
                recent.forEach(project -> menu.add(menuItem(
                        project.displayName(),
                        () -> {
                            showMainWindow();
                            mainController.showProjectPage(project);
                        }
                )));
            }
        } catch (Exception exception) {
            var unavailable = new MenuItem("最近项目：读取失败");
            unavailable.setEnabled(false);
            menu.add(unavailable);
        }
        menu.addSeparator();
        menu.add(menuItem("刷新项目", () -> {
            gitStatusService.refreshAll();
            mainController.showProjectHomePage();
        }));
        menu.add(menuItem("设置", () -> {
            showMainWindow();
            mainController.showSettingsPage();
        }));
        menu.addSeparator();
        menu.add(menuItem("退出软件", () -> {
            exiting = true;
            Platform.exit();
        }));
        trayIcon = new TrayIcon(createAwtIcon(32), "本地 Git 项目管理器", menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(event -> Platform.runLater(this::showMainWindow));
        try {
            SystemTray.getSystemTray().add(trayIcon);
            Platform.setImplicitExit(false);
        } catch (AWTException exception) {
            trayIcon = null;
        }
    }

    private MenuItem menuItem(String text, Runnable action) {
        var item = new MenuItem(text);
        item.addActionListener(event -> Platform.runLater(action));
        return item;
    }

    private void handleCloseRequest(WindowEvent event) {
        if (exiting) {
            return;
        }
        var behavior = settingsService.closeBehavior();
        if (behavior == ApplicationSettingsService.CloseBehavior.ASK && trayIcon != null) {
            var minimize = new ButtonType("最小化到托盘");
            var exit = new ButtonType("退出软件");
            var alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("关闭窗口");
            alert.setHeaderText("关闭后如何处理？");
            alert.setContentText("可以继续在系统托盘中运行，设置中可随时修改。");
            alert.getButtonTypes().setAll(minimize, exit, ButtonType.CANCEL);
            DialogTheme.apply(alert);
            var choice = alert.showAndWait().orElse(ButtonType.CANCEL);
            if (choice == ButtonType.CANCEL) {
                event.consume();
                return;
            }
            behavior = choice == minimize
                    ? ApplicationSettingsService.CloseBehavior.MINIMIZE_TO_TRAY
                    : ApplicationSettingsService.CloseBehavior.EXIT_APPLICATION;
            settingsService.setCloseBehavior(behavior);
        }
        if (behavior == ApplicationSettingsService.CloseBehavior.MINIMIZE_TO_TRAY && trayIcon != null) {
            event.consume();
            stage.hide();
        } else {
            exiting = true;
            Platform.exit();
        }
    }

    private void showMainWindow() {
        stage.show();
        stage.toFront();
        stage.requestFocus();
    }

    private static WritableImage createFxIcon(int size) {
        var image = new WritableImage(size, size);
        var writer = image.getPixelWriter();
        for (var y = 0; y < size; y++) {
            for (var x = 0; x < size; x++) {
                var inside = x > size / 8 && x < size * 7 / 8 && y > size / 8 && y < size * 7 / 8;
                writer.setColor(x, y, inside ? Color.web("#4aabb8") : Color.TRANSPARENT);
            }
        }
        return image;
    }

    private static BufferedImage createAwtIcon(int size) {
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        graphics.setColor(new java.awt.Color(74, 171, 184));
        graphics.fillRoundRect(2, 2, size - 4, size - 4, size / 3, size / 3);
        graphics.setColor(java.awt.Color.WHITE);
        graphics.drawString("G", size / 3, size * 2 / 3);
        graphics.dispose();
        return image;
    }

    private void applyTheme(ThemeMode theme) {
        if (scene == null) {
            return;
        }
        var light = Objects.requireNonNull(
                getClass().getResource("/css/light-theme.css"),
                "Missing light-theme.css"
        ).toExternalForm();
        scene.getStylesheets().remove(light);
        var useLight = theme == ThemeMode.LIGHT
                || theme == ThemeMode.SYSTEM
                && Platform.getPreferences().getColorScheme() == ColorScheme.LIGHT;
        if (useLight) {
            scene.getStylesheets().add(light);
        }
        DialogTheme.setStylesheets(scene.getStylesheets());
    }

    @Override
    public void stop() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
        if (gitStatusService != null) {
            gitStatusService.close();
        }
        if (gitOperationService != null) {
            gitOperationService.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
