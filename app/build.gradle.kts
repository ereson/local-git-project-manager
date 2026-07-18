plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.localprojectmanager"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.1")
    implementation("org.slf4j:slf4j-api:2.0.17")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.18")
    implementation("org.xerial:sqlite-jdbc:3.53.1.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainModule = "com.localprojectmanager.app"
    mainClass = "com.localprojectmanager.bootstrap.Launcher"
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=javafx.graphics,org.xerial.sqlitejdbc"
    )
}

javafx {
    version = "26.0.1"
    modules("javafx.controls", "javafx.fxml")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

val verifyFxml = tasks.register("verifyFxml") {
    val fxmlFiles = fileTree("src/main/resources/fxml") {
        include("**/*.fxml")
    }
    inputs.files(fxmlFiles)
    doLast {
        val parser = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
        fxmlFiles.forEach { parser.parse(it) }
    }
}

tasks.named("check") {
    dependsOn(verifyFxml)
}

val jpackageExecutable = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(25)
}.map { it.metadata.installationPath.file("bin/jpackage.exe").asFile.absolutePath }
val installedLibraries = layout.buildDirectory.dir("install/app/lib")
val packageRoot = layout.buildDirectory.dir("jpackage")

tasks.register<Exec>("jpackagePortable") {
    group = "distribution"
    description = "Builds the self-contained Windows portable app image."
    dependsOn("installDist")
    doFirst {
        val destination = packageRoot.get().dir("portable").asFile
        delete(destination)
        commandLine(
            jpackageExecutable.get(),
            "--type", "app-image",
            "--name", "LocalProjectManager",
            "--app-version", "0.1.0",
            "--input", installedLibraries.get().asFile.absolutePath,
            "--main-jar", "app-${project.version}.jar",
            "--main-class", "com.localprojectmanager.bootstrap.Launcher",
            "--dest", destination.absolutePath,
            "--arguments", "--portable",
            "--java-options", "--enable-native-access=javafx.graphics,org.xerial.sqlitejdbc"
        )
    }
}

tasks.register<Zip>("portableZip") {
    group = "distribution"
    description = "Builds the distributable portable ZIP."
    dependsOn("jpackagePortable")
    from(packageRoot.map { it.dir("portable") })
    archiveFileName.set("LocalProjectManager-0.1.0-portable.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}

tasks.register<Exec>("jpackageInstaller") {
    group = "distribution"
    description = "Builds the Windows MSI installer (requires WiX)."
    dependsOn("installDist")
    doFirst {
        val destination = packageRoot.get().dir("installer").asFile
        delete(destination)
        commandLine(
            jpackageExecutable.get(),
            "--type", "msi",
            "--name", "LocalProjectManager",
            "--app-version", "0.1.0",
            "--vendor", "LocalProjectManager",
            "--install-dir", "LocalProjectManagerApp",
            "--input", installedLibraries.get().asFile.absolutePath,
            "--main-jar", "app-${project.version}.jar",
            "--main-class", "com.localprojectmanager.bootstrap.Launcher",
            "--dest", destination.absolutePath,
            "--win-menu", "--win-shortcut", "--win-per-user-install",
            "--java-options", "--enable-native-access=javafx.graphics,org.xerial.sqlitejdbc"
        )
    }
}
