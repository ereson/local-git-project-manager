package com.localprojectmanager.application.update;

import com.localprojectmanager.domain.update.UpdateCheckResult;
import com.localprojectmanager.domain.update.UpdateManifest;
import com.localprojectmanager.domain.update.UpdatePackageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UpdateServiceTest {

    private static final URI MANIFEST_URI = URI.create("https://updates.example.test/version.json");

    @TempDir
    Path tempDirectory;

    @Test
    void checksManifestWithoutDownloadingPackage() throws Exception {
        var calls = new AtomicInteger();
        var packageBytes = "package".getBytes(StandardCharsets.UTF_8);
        var service = new UpdateService(uri -> {
            calls.incrementAndGet();
            if (!uri.equals(MANIFEST_URI)) {
                throw new AssertionError("check must not download an update package");
            }
            return response(manifest("1.1.0", sha256(packageBytes)));
        }, MANIFEST_URI, "1.0.9", tempDirectory.resolve("updates"));

        var result = service.check();

        assertEquals(UpdateCheckResult.Status.AVAILABLE, result.status());
        assertEquals("1.1.0", result.update().version());
        assertEquals(1, calls.get());
        assertFalse(Files.exists(tempDirectory.resolve("updates")));
    }

    @Test
    void containsCheckFailuresAndRejectsNonHttpsBeforeTransport() {
        var calls = new AtomicInteger();
        var failing = new UpdateService(uri -> {
            calls.incrementAndGet();
            throw new IOException("offline");
        }, MANIFEST_URI, "1.0.0", tempDirectory);
        var insecure = new UpdateService(uri -> {
            calls.incrementAndGet();
            return response("{}");
        }, URI.create("http://updates.example.test/version.json"), "1.0.0", tempDirectory);

        assertEquals(UpdateCheckResult.Status.FAILED, failing.check().status());
        assertEquals(UpdateCheckResult.Status.FAILED, insecure.check().status());
        assertEquals(1, calls.get());
    }

    @Test
    void downloadsOnlyWhenRequestedAndVerifiesSha256() throws Exception {
        var bytes = "verified update".getBytes(StandardCharsets.UTF_8);
        var hash = sha256(bytes);
        var service = new UpdateService(uri -> new UpdateService.Response(
                200, new ByteArrayInputStream(bytes), bytes.length
        ), MANIFEST_URI, "1.0.0", tempDirectory.resolve("updates"));
        var manifest = new UpdateService(
                uri -> response(manifest("2.0.0", hash)),
                MANIFEST_URI,
                "1.0.0",
                tempDirectory
        ).check().update();

        var result = service.download(manifest, UpdatePackageType.INSTALLER);

        assertTrue(result.successful());
        assertEquals("local-project-manager.exe", result.file().getFileName().toString());
        assertArrayEquals(bytes, Files.readAllBytes(result.file()));
    }

    @Test
    void deletesPartialFileWhenSha256DoesNotMatch() throws Exception {
        var bytes = "tampered".getBytes(StandardCharsets.UTF_8);
        var service = new UpdateService(uri -> new UpdateService.Response(
                200, new ByteArrayInputStream(bytes), bytes.length
        ), MANIFEST_URI, "1.0.0", tempDirectory.resolve("updates"));
        var manifest = new UpdateService(
                uri -> response(manifest("2.0.0", sha256("expected".getBytes(StandardCharsets.UTF_8)))),
                MANIFEST_URI,
                "1.0.0",
                tempDirectory
        ).check().update();

        var result = service.download(manifest, UpdatePackageType.PORTABLE);

        assertFalse(result.successful());
        assertNull(result.file());
        try (var files = Files.list(tempDirectory.resolve("updates"))) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void rejectsOversizedPackageBeforeWriting() throws Exception {
        var service = new UpdateService(uri -> new UpdateService.Response(
                200, new ByteArrayInputStream(new byte[0]), 513L * 1024 * 1024
        ), MANIFEST_URI, "1.0.0", tempDirectory.resolve("updates"));
        var manifest = new UpdateService(
                uri -> response(manifest("2.0.0", "0".repeat(64))),
                MANIFEST_URI,
                "1.0.0",
                tempDirectory
        ).check().update();

        var result = service.download(manifest, UpdatePackageType.INSTALLER);

        assertFalse(result.successful());
        assertFalse(Files.exists(tempDirectory.resolve("updates")));
    }

    @Test
    void rejectsNonHttpsPackageBeforeTransport() {
        var calls = new AtomicInteger();
        var service = new UpdateService(uri -> {
            calls.incrementAndGet();
            return response("unused");
        }, MANIFEST_URI, "1.0.0", tempDirectory.resolve("updates"));
        var manifest = new UpdateManifest(
                "2.0.0",
                Instant.parse("2026-08-01T10:00:00Z"),
                List.of(),
                URI.create("http://downloads.example.test/update.exe"),
                URI.create("https://downloads.example.test/update.zip"),
                "0".repeat(64)
        );

        var result = service.download(manifest, UpdatePackageType.INSTALLER);

        assertFalse(result.successful());
        assertEquals(0, calls.get());
        assertFalse(Files.exists(tempDirectory.resolve("updates")));
    }

    private static UpdateService.Response response(String value) {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        return new UpdateService.Response(200, new ByteArrayInputStream(bytes), bytes.length);
    }

    private static String manifest(String version, String sha256) {
        return """
                {
                  "version": "%s",
                  "publishedAt": "2026-08-01T10:00:00Z",
                  "releaseNotes": ["修复更新问题"],
                  "installerUrl": "https://downloads.example.test/local-project-manager.exe",
                  "portableUrl": "https://downloads.example.test/local-project-manager.zip",
                  "sha256": "%s"
                }
                """.formatted(version, sha256);
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
