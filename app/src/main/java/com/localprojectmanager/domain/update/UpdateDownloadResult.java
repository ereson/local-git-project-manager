package com.localprojectmanager.domain.update;

import java.nio.file.Path;

public record UpdateDownloadResult(boolean successful, Path file, String message) {
}
