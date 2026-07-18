package com.localprojectmanager.ui;

import com.localprojectmanager.application.settings.ApplicationSettingsService;
import com.localprojectmanager.application.settings.ApplicationSettingsService.CloseBehavior;
import com.localprojectmanager.application.settings.ApplicationSettingsService.ThemeMode;
import com.localprojectmanager.domain.project.PullStrategy;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

import java.util.Objects;
import java.util.function.Consumer;

public final class SettingsViewController {

    @FXML private ComboBox<ThemeMode> themeChoice;
    @FXML private ComboBox<CloseBehavior> closeBehaviorChoice;
    @FXML private ComboBox<PullStrategy> pullStrategyChoice;
    @FXML private CheckBox updateCheckBox;
    @FXML private Label statusLabel;
    @FXML private TextArea ignoreRulesText;

    private ApplicationSettingsService settings;
    private Consumer<ThemeMode> onThemeChanged;
    private Runnable onBack;
    private Runnable onCheckUpdates;
    private Runnable onOpenLogs;

    void configure(
            ApplicationSettingsService settings,
            Consumer<ThemeMode> onThemeChanged,
            Runnable onCheckUpdates,
            Runnable onOpenLogs,
            Runnable onBack
    ) {
        this.settings = Objects.requireNonNull(settings);
        this.onThemeChanged = Objects.requireNonNull(onThemeChanged);
        this.onCheckUpdates = Objects.requireNonNull(onCheckUpdates);
        this.onOpenLogs = Objects.requireNonNull(onOpenLogs);
        this.onBack = Objects.requireNonNull(onBack);
        themeChoice.setItems(FXCollections.observableArrayList(ThemeMode.values()));
        closeBehaviorChoice.setItems(FXCollections.observableArrayList(CloseBehavior.values()));
        pullStrategyChoice.setItems(FXCollections.observableArrayList(PullStrategy.values()));
        themeChoice.setValue(settings.theme());
        closeBehaviorChoice.setValue(settings.closeBehavior());
        pullStrategyChoice.setValue(settings.globalPullStrategy());
        updateCheckBox.setSelected(settings.checkUpdatesOnStartup());
        ignoreRulesText.setText(settings.customIgnoreRules());
    }

    @FXML private void saveTheme() { save(() -> { settings.setTheme(themeChoice.getValue()); onThemeChanged.accept(themeChoice.getValue()); }); }
    @FXML private void saveCloseBehavior() { save(() -> settings.setCloseBehavior(closeBehaviorChoice.getValue())); }
    @FXML private void savePullStrategy() { save(() -> settings.setGlobalPullStrategy(pullStrategyChoice.getValue())); }
    @FXML private void saveUpdateSetting() { save(() -> settings.setCheckUpdatesOnStartup(updateCheckBox.isSelected())); }
    @FXML private void saveIgnoreRules() { save(() -> settings.setCustomIgnoreRules(ignoreRulesText.getText())); }
    @FXML private void checkUpdates() { onCheckUpdates.run(); }
    @FXML private void openLogs() { onOpenLogs.run(); }
    @FXML private void goBack() { onBack.run(); }

    private void save(Runnable action) {
        try {
            action.run();
            statusLabel.setText("设置已保存。");
        } catch (IllegalStateException exception) {
            statusLabel.setText("设置保存失败，请稍后重试。");
        }
    }
}
