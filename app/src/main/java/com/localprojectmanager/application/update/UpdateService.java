package com.localprojectmanager.application.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localprojectmanager.domain.update.UpdateCheckResult;
import com.localprojectmanager.domain.update.UpdateDownloadResult;
import com.localprojectmanager.domain.update.UpdateManifest;
import com.localprojectmanager.domain.update.UpdatePackageType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class UpdateService {

    private static final long MAX_MANIFEST_BYTES = 1024 * 1024;
    private static final long MAX_PACKAGE_BYTES = 512L * 1024 * 1024;
    private static final Pattern VERSION = Pattern.compile(
            "[0-9]+(?:\\.[0-9]+)*(?:-[0-9A-Za-z.-]+)?"
    );
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");

    private final Transport transport;
    private final URI manifestUri;
    private final String currentVersion;
    private final Path updatesDirectory;
    private final ObjectMapper json = new ObjectMapper();

    public UpdateService(
            HttpClient httpClient,
            URI manifestUri,
            String currentVersion,
            Path updatesDirectory
    ) {
        this(uri -> {
            var response = Objects.requireNonNull(httpClient).send(
                    HttpRequest.newBuilder(uri).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream()
            );
            return new Response(
                    response.statusCode(),
                    response.body(),
                    response.headers().firstValueAsLong("Content-Length").orElse(-1)
            );
        }, manifestUri, currentVersion, updatesDirectory);
    }

    UpdateService(
            Transport transport,
            URI manifestUri,
            String currentVersion,
            Path updatesDirectory
    ) {
        this.transport = Objects.requireNonNull(transport);
        this.manifestUri = Objects.requireNonNull(manifestUri);
        this.currentVersion = requireVersion(currentVersion);
        this.updatesDirectory = Objects.requireNonNull(updatesDirectory).toAbsolutePath().normalize();
    }

    public UpdateCheckResult check() {
        try {
            requireHttps(manifestUri);
            var manifest = requestManifest();
            return compareVersions(manifest.version(), currentVersion) > 0
                    ? new UpdateCheckResult(
                            UpdateCheckResult.Status.AVAILABLE,
                            manifest,
                            "发现新版本 " + manifest.version() + "。"
                    )
                    : new UpdateCheckResult(
                            UpdateCheckResult.Status.CURRENT,
                            null,
                            "当前已是最新版本。"
                    );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failedCheck();
        } catch (Exception exception) {
            return failedCheck();
        }
    }

    public UpdateDownloadResult download(UpdateManifest manifest, UpdatePackageType packageType) {
        Objects.requireNonNull(manifest);
        Objects.requireNonNull(packageType);
        Path temporary = null;
        try {
            var uri = packageType == UpdatePackageType.INSTALLER
                    ? manifest.installerUrl()
                    : manifest.portableUrl();
            requireHttps(uri);
            requireSha256(manifest.sha256());

            var fileName = fileName(uri);
            var target = updatesDirectory.resolve(fileName).normalize();
            if (!target.getParent().equals(updatesDirectory)) {
                throw new IllegalArgumentException("Invalid update file name");
            }

            var response = transport.get(uri);
            try (var body = response.body()) {
                requireSuccessful(response.statusCode());
                requireSize(response.contentLength(), MAX_PACKAGE_BYTES);
                Files.createDirectories(updatesDirectory);
                temporary = Files.createTempFile(updatesDirectory, "update-", ".part");
                var digest = MessageDigest.getInstance("SHA-256");
                copy(body, temporary, digest);
                var actual = HexFormat.of().formatHex(digest.digest());
                if (!actual.equalsIgnoreCase(manifest.sha256())) {
                    return new UpdateDownloadResult(false, null, "更新包校验失败。");
                }
                move(temporary, target);
                temporary = null;
                return new UpdateDownloadResult(true, target, "更新包下载完成。");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new UpdateDownloadResult(false, null, "更新包下载已取消。");
        } catch (Exception exception) {
            return new UpdateDownloadResult(false, null, "更新包下载失败。");
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // A later cleanup can remove an abandoned partial download.
                }
            }
        }
    }

    private UpdateManifest requestManifest() throws Exception {
        var response = transport.get(manifestUri);
        try (var body = response.body()) {
            requireSuccessful(response.statusCode());
            requireSize(response.contentLength(), MAX_MANIFEST_BYTES);
            var root = json.readTree(readLimited(body, MAX_MANIFEST_BYTES));
            var version = requireVersion(text(root, "version"));
            var publishedAt = Instant.parse(text(root, "publishedAt"));
            var releaseNotes = strings(root.required("releaseNotes"));
            var installerUrl = requireHttps(URI.create(text(root, "installerUrl")));
            var portableUrl = requireHttps(URI.create(text(root, "portableUrl")));
            var sha256 = requireSha256(text(root, "sha256"));
            return new UpdateManifest(
                    version, publishedAt, releaseNotes, installerUrl, portableUrl, sha256
            );
        }
    }

    private static String text(JsonNode root, String field) {
        var value = root.required(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return value.textValue();
    }

    private static List<String> strings(JsonNode node) {
        if (!node.isArray()) {
            throw new IllegalArgumentException("Invalid releaseNotes");
        }
        var values = new ArrayList<String>();
        node.forEach(value -> {
            if (!value.isTextual()) {
                throw new IllegalArgumentException("Invalid releaseNotes");
            }
            values.add(value.textValue());
        });
        return List.copyOf(values);
    }

    private static byte[] readLimited(InputStream input, long maximum) throws IOException {
        var output = new ByteArrayOutputStream();
        var buffer = new byte[8192];
        long total = 0;
        for (int read; (read = input.read(buffer)) >= 0;) {
            total += read;
            if (total > maximum) {
                throw new IOException("Response is too large");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void copy(InputStream input, Path target, MessageDigest digest)
            throws IOException {
        try (OutputStream output = Files.newOutputStream(target)) {
            var buffer = new byte[8192];
            long total = 0;
            for (int read; (read = input.read(buffer)) >= 0;) {
                total += read;
                if (total > MAX_PACKAGE_BYTES) {
                    throw new IOException("Update package is too large");
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String fileName(URI uri) {
        var path = uri.getPath();
        var slash = path.lastIndexOf('/');
        var name = slash < 0 ? path : path.substring(slash + 1);
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Invalid update file name");
        }
        return name;
    }

    private static URI requireHttps(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("HTTPS is required");
        }
        return uri;
    }

    private static String requireSha256(String sha256) {
        if (sha256 == null || !SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("Invalid SHA-256");
        }
        return sha256;
    }

    private static String requireVersion(String version) {
        if (version == null || !VERSION.matcher(version).matches()) {
            throw new IllegalArgumentException("Invalid version");
        }
        return version;
    }

    private static void requireSuccessful(int statusCode) throws IOException {
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("Unexpected HTTP status " + statusCode);
        }
    }

    private static void requireSize(long size, long maximum) throws IOException {
        if (size > maximum) {
            throw new IOException("Response is too large");
        }
    }

    private static int compareVersions(String left, String right) {
        var leftVersion = ParsedVersion.parse(left);
        var rightVersion = ParsedVersion.parse(right);
        var length = Math.max(leftVersion.numbers().size(), rightVersion.numbers().size());
        for (var index = 0; index < length; index++) {
            var leftNumber = index < leftVersion.numbers().size()
                    ? leftVersion.numbers().get(index) : BigInteger.ZERO;
            var rightNumber = index < rightVersion.numbers().size()
                    ? rightVersion.numbers().get(index) : BigInteger.ZERO;
            var result = leftNumber.compareTo(rightNumber);
            if (result != 0) {
                return result;
            }
        }
        if (Objects.equals(leftVersion.preRelease(), rightVersion.preRelease())) {
            return 0;
        }
        if (leftVersion.preRelease() == null) {
            return 1;
        }
        if (rightVersion.preRelease() == null) {
            return -1;
        }
        return leftVersion.preRelease().compareToIgnoreCase(rightVersion.preRelease());
    }

    private static UpdateCheckResult failedCheck() {
        return new UpdateCheckResult(
                UpdateCheckResult.Status.FAILED,
                null,
                "无法检查软件更新。"
        );
    }

    private record ParsedVersion(List<BigInteger> numbers, String preRelease) {

        private static ParsedVersion parse(String version) {
            var dash = version.indexOf('-');
            var numbers = (dash < 0 ? version : version.substring(0, dash)).split("\\.");
            return new ParsedVersion(
                    java.util.Arrays.stream(numbers).map(BigInteger::new).toList(),
                    dash < 0 ? null : version.substring(dash + 1)
            );
        }
    }

    @FunctionalInterface
    interface Transport {
        Response get(URI uri) throws IOException, InterruptedException;
    }

    record Response(int statusCode, InputStream body, long contentLength) {

        Response {
            Objects.requireNonNull(body);
        }
    }
}
