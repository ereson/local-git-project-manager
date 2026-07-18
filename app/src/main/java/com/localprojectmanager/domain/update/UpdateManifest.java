package com.localprojectmanager.domain.update;

import java.net.URI;
import java.time.Instant;
import java.util.List;

public record UpdateManifest(
        String version,
        Instant publishedAt,
        List<String> releaseNotes,
        URI installerUrl,
        URI portableUrl,
        String sha256
) {

    public UpdateManifest {
        releaseNotes = List.copyOf(releaseNotes);
    }
}
