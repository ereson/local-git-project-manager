package com.localprojectmanager.ui;

import com.localprojectmanager.application.scan.ScanRootService;
import com.localprojectmanager.application.scan.GitRepositoryScanner.DiscoveredRepository;
import com.localprojectmanager.application.scan.GitRepositoryScanner.ScanResult;
import com.localprojectmanager.domain.scan.ScanRoot;
import com.localprojectmanager.application.settings.ApplicationSettingsService;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Objects;

public final class ScanRootsViewModel {

    private final ScanRootService service;
    private final ObservableList<ScanRoot> scanRoots = FXCollections.observableArrayList();
    private final SimpleBooleanProperty useDefaultIgnoreRules = new SimpleBooleanProperty(true);
    private final SimpleStringProperty message = new SimpleStringProperty("");
    private final ApplicationSettingsService settings;

    public ScanRootsViewModel(ScanRootService service) {
        this(service, null);
    }

    public ScanRootsViewModel(ScanRootService service, ApplicationSettingsService settings) {
        this.service = Objects.requireNonNull(service);
        this.settings = settings;
    }

    public ObservableList<ScanRoot> scanRoots() {
        return scanRoots;
    }

    public ReadOnlyStringProperty messageProperty() {
        return message;
    }

    public BooleanProperty useDefaultIgnoreRulesProperty() {
        return useDefaultIgnoreRules;
    }

    public boolean useDefaultIgnoreRules() {
        return useDefaultIgnoreRules.get();
    }

    public java.util.List<String> customIgnoreRules() {
        return settings == null ? java.util.List.of() : settings.customIgnoreRules().lines().toList();
    }

    public void load() {
        try {
            scanRoots.setAll(service.list());
            message.set("");
        } catch (SQLException exception) {
            message.set("无法加载扫描目录，请稍后重试。");
        }
    }

    public void add(Path path) {
        try {
            var result = service.add(path);
            scanRoots.add(result.scanRoot());
            message.set(result.overlapsExisting()
                    ? "该目录与已有扫描目录存在包含关系，扫描结果可能重复。"
                    : "已添加扫描目录。");
        } catch (IllegalArgumentException exception) {
            message.set(exception.getMessage());
        } catch (SQLException exception) {
            message.set("无法保存扫描目录，请稍后重试。");
        }
    }

    public void remove(ScanRoot scanRoot) {
        if (scanRoot == null) {
            return;
        }
        try {
            if (service.remove(scanRoot.id())) {
                scanRoots.remove(scanRoot);
                message.set("已移除扫描目录。");
            }
        } catch (SQLException exception) {
            message.set("无法移除扫描目录，请稍后重试。");
        }
    }

    public void scanStarted() {
        message.set("正在扫描 Git 项目…");
    }

    public void scanCompleted(ScanResult result) {
        var nestedCount = result.repositories().stream()
                .filter(DiscoveredRepository::nestedRepository)
                .count();
        var nested = nestedCount == 0 ? "" : "，其中 " + nestedCount + " 个嵌套项目";
        var warning = result.failedPaths().isEmpty()
                ? ""
                : "，另有 " + result.failedPaths().size() + " 个路径无法访问";
        message.set("已发现 " + result.repositories().size() + " 个 Git 项目" + nested + warning + "。");
    }

    public void scanCancelled() {
        message.set("扫描已取消。");
    }

    public void scanFailed() {
        message.set("扫描失败，请稍后重试。");
    }
}
