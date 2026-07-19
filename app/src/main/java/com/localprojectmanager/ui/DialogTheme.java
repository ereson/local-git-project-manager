package com.localprojectmanager.ui;

import javafx.scene.control.Dialog;

import java.util.List;
import java.util.Objects;

public final class DialogTheme {

    private static List<String> stylesheets = List.of();

    private DialogTheme() {
    }

    public static void setStylesheets(List<String> stylesheets) {
        DialogTheme.stylesheets = List.copyOf(stylesheets);
    }

    public static void apply(Dialog<?> dialog) {
        var pane = Objects.requireNonNull(dialog).getDialogPane();
        pane.getStylesheets().setAll(stylesheets);
        if (!pane.getStyleClass().contains("app-root")) {
            pane.getStyleClass().add("app-root");
        }
    }
}
