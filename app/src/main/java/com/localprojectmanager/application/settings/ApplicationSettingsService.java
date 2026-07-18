package com.localprojectmanager.application.settings;

import com.localprojectmanager.domain.project.PullStrategy;
import com.localprojectmanager.infrastructure.database.ApplicationSettingsRepository;

import java.sql.SQLException;
import java.util.Objects;

public final class ApplicationSettingsService {

    private final ApplicationSettingsRepository settings;

    public ApplicationSettingsService(ApplicationSettingsRepository settings) {
        this.settings = Objects.requireNonNull(settings);
    }

    public ThemeMode theme() { return read("theme", ThemeMode.class, ThemeMode.SYSTEM); }
    public void setTheme(ThemeMode value) { write("theme", value.name()); }
    public CloseBehavior closeBehavior() { return read("close_behavior", CloseBehavior.class, CloseBehavior.ASK); }
    public void setCloseBehavior(CloseBehavior value) { write("close_behavior", value.name()); }
    public ViewMode viewMode() { return read("home_view", ViewMode.class, ViewMode.CARD); }
    public void setViewMode(ViewMode value) { write("home_view", value.name()); }
    public PullStrategy globalPullStrategy() { return read("global_pull_strategy", PullStrategy.class, PullStrategy.REBASE); }
    public void setGlobalPullStrategy(PullStrategy value) { write("global_pull_strategy", value.name()); }
    public boolean checkUpdatesOnStartup() {
        try {
            return settings.getBoolean("check_updates_on_startup", true);
        } catch (SQLException exception) {
            return true;
        }
    }
    public void setCheckUpdatesOnStartup(boolean value) { write("check_updates_on_startup", Boolean.toString(value)); }
    public String customIgnoreRules() {
        try {
            return settings.get("custom_ignore_rules", "");
        } catch (SQLException exception) {
            return "";
        }
    }
    public void setCustomIgnoreRules(String value) { write("custom_ignore_rules", Objects.requireNonNull(value)); }

    private <E extends Enum<E>> E read(String key, Class<E> type, E fallback) {
        try {
            return settings.getEnum(key, type, fallback);
        } catch (SQLException exception) {
            return fallback;
        }
    }

    private void write(String key, String value) {
        try {
            settings.put(key, value);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save setting " + key, exception);
        }
    }

    public enum ThemeMode {
        SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色");
        private final String text;
        ThemeMode(String text) { this.text = text; }
        @Override public String toString() { return text; }
    }

    public enum CloseBehavior {
        ASK("首次询问"), EXIT_APPLICATION("退出软件"), MINIMIZE_TO_TRAY("最小化到托盘");
        private final String text;
        CloseBehavior(String text) { this.text = text; }
        @Override public String toString() { return text; }
    }

    public enum ViewMode {
        CARD, TABLE
    }
}
