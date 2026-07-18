package com.localprojectmanager.ui;

import com.localprojectmanager.application.scan.GitRepositoryScanner.DiscoveredRepository;
import com.localprojectmanager.application.scan.GitRepositoryScanner.ScanResult;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public final class ScanResultsViewModel {

    private final ObservableList<DiscoveredRepository> repositories;
    private final List<Path> failedPaths;
    private final LinkedHashSet<DiscoveredRepository> selected = new LinkedHashSet<>();
    private final LinkedHashSet<DiscoveredRepository> imported = new LinkedHashSet<>();
    private final SimpleIntegerProperty selectedCount = new SimpleIntegerProperty();

    public ScanResultsViewModel(ScanResult result) {
        Objects.requireNonNull(result, "result");
        repositories = FXCollections.observableArrayList(result.repositories());
        failedPaths = result.failedPaths();
        selectAll();
    }

    public ObservableList<DiscoveredRepository> repositories() {
        return repositories;
    }

    public ReadOnlyIntegerProperty selectedCountProperty() {
        return selectedCount;
    }

    public boolean isSelected(DiscoveredRepository repository) {
        return selected.contains(repository);
    }

    public void setSelected(DiscoveredRepository repository, boolean value) {
        if (imported.contains(repository)) {
            return;
        }
        if (value) {
            selected.add(repository);
        } else {
            selected.remove(repository);
        }
        selectedCount.set(selected.size());
    }

    public void selectAll() {
        repositories.stream().filter(repository -> !imported.contains(repository))
                .forEach(selected::add);
        selectedCount.set(selected.size());
    }

    public void clearSelection() {
        selected.clear();
        selectedCount.set(0);
    }

    public List<DiscoveredRepository> selectedRepositories() {
        return repositories.stream().filter(selected::contains).toList();
    }

    public boolean isImported(DiscoveredRepository repository) {
        return imported.contains(repository);
    }

    public void markImported(List<DiscoveredRepository> repositories) {
        imported.addAll(repositories);
        selected.removeAll(repositories);
        selectedCount.set(selected.size());
    }

    public String warningText() {
        return failedPaths.isEmpty()
                ? ""
                : "⚠ 有 " + failedPaths.size() + " 个路径无法访问，未完成扫描（悬停查看详情）。";
    }

    public String warningDetails() {
        return String.join("\n", failedPaths.stream().map(Path::toString).toList());
    }
}
