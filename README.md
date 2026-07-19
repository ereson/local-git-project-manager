# Local Git Project Manager

[English](README.md) | [简体中文](README.zh-CN.md)

A local-first Windows desktop application for discovering, organizing, opening, and safely synchronizing Git repositories on your computer.

The application is built with JavaFX and stores all project metadata locally in SQLite. It does not require an account or upload repository information.

## Features

### Project management

- Scan one or more directories for Git repositories.
- Add a repository manually.
- Detect nested repositories and avoid duplicate imports.
- Search by display name, directory name, or full path.
- Switch between card and table views.
- Rename, relocate, or remove a project record without changing project files.
- Detect unavailable and inaccessible project paths.
- Launch a project directly from its card when a default IDE is configured.

### IDE integration

- Detect Visual Studio Code.
- Detect standalone and JetBrains Toolbox installations.
- Keep multiple installed IDE versions.
- Add another IDE manually.
- Save a different default IDE for each project.
- Open a project with its default IDE or temporarily use another IDE.
- Open project directories in File Explorer or a terminal.

### Git operations

- Show the current branch, uncommitted file count, latest commit, remote URL, and cached ahead/behind counts.
- Refresh local status in the background without blocking the JavaFX thread.
- Run Fetch only when requested.
- List and search local and remote branches.
- Switch local branches or create a local tracking branch from a remote branch.
- Pull using Rebase, Merge, or the repository's Git configuration.
- Detect conflicts and direct the user to an IDE or terminal for resolution.
- Keep the latest Git operation result for each project.

### Desktop experience

- Chinese application interface.
- Light, dark, and Windows system themes, including JavaFX dialogs.
- Configurable close behavior and Windows system tray support.
- Local error logs.
- Optional update checks and SHA-256 verification before opening a downloaded package.
- Portable ZIP and per-user MSI packages.

## Safety and scope

This application intentionally provides a limited Git surface. It does **not** implement:

- Commit or Push
- Force Push
- Reset or Clean
- Automatic Stash
- File checkout or discard
- Branch deletion
- Conflict resolution
- Credential, token, password, or SSH key storage

Git commands are executed with `ProcessBuilder` argument lists rather than shell-concatenated commands. Pull is blocked when the working tree contains uncommitted changes, and repository files are never deleted when a project is removed from the application.

## Requirements

- Windows 10 or later
- JDK 25 for development and packaging
- Git for status, Fetch, Pull, and branch operations
- WiX Toolset 3.14 or later only when building the MSI installer

The packaged application includes its Java runtime, so end users do not need to install a JDK.

## Quick start from source

Open PowerShell in the repository root and point `JAVA_HOME` to JDK 25:

```powershell
$env:JAVA_HOME="C:\path\to\jdk-25"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

.\gradlew.bat :app:run
```

On first launch:

1. Select one or more directories to scan, or add a Git project manually.
2. Review the discovered repositories and import the projects you want.
3. Open a project detail page and select its default IDE.
4. Use the project card's **Launch Project** action for later launches.

## Build and test

```powershell
# Run unit and integration tests
.\gradlew.bat test

# Run tests plus FXML validation
.\gradlew.bat check

# Clean and build the complete project
.\gradlew.bat clean build
```

Git integration tests create temporary local repositories. They do not modify repositories managed by the application.

## Distribution packages

```powershell
# Portable application ZIP
.\gradlew.bat :app:portableZip

# Windows MSI installer (requires WiX)
.\gradlew.bat :app:jpackageInstaller
```

Generated files:

| Package | Output |
| --- | --- |
| Portable ZIP | `app/build/distributions/LocalProjectManager-0.1.0-portable.zip` |
| MSI installer | `app/build/jpackage/installer/LocalProjectManager-0.1.0.msi` |

## Local data

| Distribution | Data directory |
| --- | --- |
| Installed | `%LOCALAPPDATA%\LocalProjectManager` |
| Portable | The writable portable application directory |

Runtime data is separated into:

```text
LocalProjectManager/
├─ data/app.db
├─ logs/
└─ updates/
```

The SQLite database stores project records, scan roots, IDE configuration, application settings, cached Git status, and the latest Git operation. Deleting an application record or uninstalling the application does not delete managed Git repositories.

## Update configuration

Update checks are disabled when no manifest URL is configured. Pass the following JVM option to enable them:

```text
-Dlpm.update.url=https://example.com/update.json
```

Expected manifest shape:

```json
{
  "version": "0.2.0",
  "publishedAt": "2026-08-01T10:00:00Z",
  "releaseNotes": ["Describe the release here"],
  "installerUrl": "https://example.com/LocalProjectManager-0.2.0.msi",
  "portableUrl": "https://example.com/LocalProjectManager-0.2.0-portable.zip",
  "sha256": "0000000000000000000000000000000000000000000000000000000000000000"
}
```

The application checks versions silently only when enabled. Downloading and opening an update always requires user confirmation.

## Architecture

```text
JavaFX FXML/CSS UI
        ↓
Controllers and ViewModels
        ↓
Application Services
        ↓
Domain Model
        ↓
SQLite / Git CLI / File System / IDE / Windows Integration
```

Key rules:

- Controllers handle UI binding, navigation, and dialogs only.
- Application services coordinate use cases.
- Repositories own all database access.
- `GitClient` owns all Git commands.
- Slow operations run outside the JavaFX Application Thread.
- Git operations for the same project are serialized.

## Technology

| Area | Technology |
| --- | --- |
| Language | Java 25 |
| Desktop UI | JavaFX 26, FXML, CSS |
| Build | Gradle Kotlin DSL |
| Storage | SQLite and JDBC |
| JSON | Jackson |
| Logging | SLF4J and Logback |
| Testing | JUnit 6 and temporary Git repositories |
| Packaging | `jpackage` |

## Project layout

```text
app/src/main/java/com/localprojectmanager/
├─ bootstrap/       Application startup and dependency wiring
├─ ui/              JavaFX controllers and view models
├─ application/     Project, scan, Git, IDE, settings, and update use cases
├─ domain/          Business entities and rules
└─ infrastructure/  SQLite, Git, IDE detection, and Windows adapters

app/src/main/resources/
├─ fxml/            JavaFX views
├─ css/             Dark and light themes
└─ database/        Versioned SQL migrations
```

## Troubleshooting

### Gradle cannot find Java 25

Install JDK 25, update `JAVA_HOME`, and verify it before running Gradle:

```powershell
java -version
.\gradlew.bat -q javaToolchains
```

### Git features are unavailable

Verify that Git is installed and available on `PATH`:

```powershell
git --version
```

Projects can still be imported and opened in an IDE when Git is unavailable, but Git status, Fetch, Pull, and branch actions are disabled.

### Portable settings cannot be saved

Move the extracted application to a writable directory. Do not run the portable build from `C:\Program Files`.

### MSI packaging fails

Install WiX Toolset 3.14 or later. WiX is not needed for the portable ZIP.

## Documentation

- [Product specification](docs/product-spec.md)
- [Architecture](docs/architecture.md)
- [Database design](docs/database.md)
- [Development plan](docs/development-plan.md)
- [Development instructions](AGENTS.md)

## License

Licensed under the [MIT License](LICENSE).
