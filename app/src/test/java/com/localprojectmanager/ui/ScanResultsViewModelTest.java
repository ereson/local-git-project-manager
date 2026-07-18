package com.localprojectmanager.ui;

import com.localprojectmanager.application.scan.GitRepositoryScanner.DiscoveredRepository;
import com.localprojectmanager.application.scan.GitRepositoryScanner.ScanResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScanResultsViewModelTest {

    @Test
    void selectsAllResultsByDefaultAndSupportsBulkAndIndividualSelection() {
        var parent = new DiscoveredRepository(Path.of("D:/projects/parent"), null);
        var child = new DiscoveredRepository(Path.of("D:/projects/parent/child"), parent.path());
        var viewModel = new ScanResultsViewModel(new ScanResult(
                List.of(parent, child),
                List.of(Path.of("D:/projects/locked")),
                false
        ));

        assertEquals(2, viewModel.selectedCountProperty().get());
        assertTrue(viewModel.isSelected(child));
        viewModel.clearSelection();
        assertTrue(viewModel.selectedRepositories().isEmpty());
        viewModel.setSelected(child, true);
        assertEquals(List.of(child), viewModel.selectedRepositories());
        viewModel.selectAll();
        assertEquals(2, viewModel.selectedRepositories().size());
        viewModel.markImported(List.of(parent));
        viewModel.selectAll();
        assertFalse(viewModel.isSelected(parent));
        assertTrue(viewModel.isImported(parent));
        assertEquals(List.of(child), viewModel.selectedRepositories());
        assertFalse(viewModel.warningText().isEmpty());
    }
}
