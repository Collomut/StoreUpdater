package com.stockmanager.util;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Properties;
import java.util.regex.*;

/**
 * Checks GitHub for a newer version of the app on startup.
 * If found, prompts the user and performs a silent download + restart.
 *
 * GitHub repo : https://github.com/Collomut/StoreUpdater
 * version.json: https://raw.githubusercontent.com/Collomut/StoreUpdater/main/version.json
 *
 * Platform support:
 *   Windows  — downloads JAR, writes updater.bat, restarts via cmd.exe
 *   Linux AppImage — downloads AppImage, writes updater.sh, replaces AppImage file
 *   Linux plain    — downloads JAR, writes updater.sh, replaces JAR in app-image dir
 */
public class AutoUpdater {

    private static final String VERSION_CHECK_URL =
        "https://raw.githubusercontent.com/Collomut/StoreUpdater/main/version.json";

    private static final boolean IS_WINDOWS =
        System.getProperty("os.name", "").toLowerCase().contains("win");
    private static final boolean IS_LINUX_APPIMAGE =
        !IS_WINDOWS && System.getenv("APPIMAGE") != null;

    /** Current version is read from version.properties bundled in the JAR. */
    private static final String CURRENT_VERSION;
    static {
        String v = "1.0.0";
        try (InputStream is = AutoUpdater.class.getResourceAsStream("/version.properties")) {
            if (is != null) {
                Properties p = new Properties();
                p.load(is);
                v = p.getProperty("app.version", "1.0.0");
            }
        } catch (Exception ignored) {}
        CURRENT_VERSION = v;
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Runs the update check in a background daemon thread so it never
     * delays the app from starting. Safe to call immediately after
     * primaryStage.show().
     */
    public static void checkForUpdatesAsync(Stage ownerStage) {
        Thread t = new Thread(() -> {
            try {
                String json = fetchUrl(VERSION_CHECK_URL, 6000);
                if (json == null) return;

                String latestVersion  = extractField(json, "version");
                String releaseNotes   = extractField(json, "release_notes");

                // Choose the right download URL for this platform
                String downloadUrl;
                if (IS_LINUX_APPIMAGE) {
                    downloadUrl = extractField(json, "download_url_linux");
                    if (downloadUrl == null) downloadUrl = extractField(json, "download_url");
                } else {
                    downloadUrl = extractField(json, "download_url");
                }

                if (latestVersion == null || downloadUrl == null) return;
                if (compareVersions(latestVersion, CURRENT_VERSION) <= 0) return;

                // Newer version found — switch to JavaFX thread for UI
                final String url   = downloadUrl;
                final String notes = releaseNotes != null ? releaseNotes : "";
                Platform.runLater(() ->
                    showUpdateDialog(ownerStage, latestVersion, url, notes));

            } catch (Exception ignored) {
                // Network unavailable or JSON malformed — app continues normally
            }
        }, "updater-check");
        t.setDaemon(true);
        t.start();
    }

    // ── Update-available dialog ───────────────────────────────────────────────

    private static void showUpdateDialog(Stage owner, String newVersion,
                                         String downloadUrl, String notes) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(owner);
        alert.setTitle("Update Available");
        alert.setHeaderText("Stock Manager v" + newVersion + " is available");
        String body = "You are currently running v" + CURRENT_VERSION + ".\n";
        if (!notes.isEmpty()) body += "\nWhat's new: " + notes + "\n";
        body += "\nUpdate now? The app will restart automatically after downloading.";
        alert.setContentText(body);

        ButtonType updateBtn = new ButtonType("Update Now", ButtonBar.ButtonData.OK_DONE);
        ButtonType skipBtn   = new ButtonType("Skip",       ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(updateBtn, skipBtn);

        alert.showAndWait().ifPresent(btn -> {
            if (btn.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                downloadAndApply(owner, newVersion, downloadUrl);
            }
        });
    }

    // ── Download + progress dialog ────────────────────────────────────────────

    private static void downloadAndApply(Stage owner, String newVersion, String downloadUrl) {
        // Determine file suffix based on what we're downloading
        String suffix = IS_LINUX_APPIMAGE ? ".AppImage" : ".jar";

        Dialog<Void> progressDialog = new Dialog<>();
        progressDialog.initOwner(owner);
        progressDialog.setTitle("Updating Stock Manager");
        progressDialog.setHeaderText("Downloading v" + newVersion + "...");

        ProgressBar bar  = new ProgressBar(0);
        bar.setPrefWidth(360);
        Label statusLbl  = new Label("Connecting...");
        VBox box = new VBox(10, statusLbl, bar);
        box.setPadding(new Insets(16, 20, 8, 20));
        progressDialog.getDialogPane().setContent(box);
        progressDialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        progressDialog.show();

        Task<Path> downloadTask = new Task<>() {
            @Override
            protected Path call() throws Exception {
                Path tmp = Files.createTempFile("StockManager-update-", suffix);
                URL url = new URL(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(120_000);
                conn.setInstanceFollowRedirects(true);
                long total = conn.getContentLengthLong();

                try (InputStream in  = conn.getInputStream();
                     OutputStream out = Files.newOutputStream(tmp)) {
                    byte[] buf = new byte[8192];
                    long downloaded = 0;
                    int  read;
                    while ((read = in.read(buf)) >= 0) {
                        out.write(buf, 0, read);
                        downloaded += read;
                        if (total > 0) updateProgress(downloaded, total);
                        updateMessage(fmt(downloaded) + (total > 0 ? " / " + fmt(total) : ""));
                    }
                }
                return tmp;
            }
        };

        bar.progressProperty().bind(downloadTask.progressProperty());
        statusLbl.textProperty().bind(downloadTask.messageProperty());

        downloadTask.setOnSucceeded(e -> {
            progressDialog.close();
            applyUpdate(downloadTask.getValue());
        });

        downloadTask.setOnFailed(e -> {
            progressDialog.close();
            new Alert(Alert.AlertType.ERROR,
                "Download failed:\n" + downloadTask.getException().getMessage(),
                ButtonType.OK).showAndWait();
        });

        progressDialog.getDialogPane().lookupButton(ButtonType.CANCEL)
            .addEventFilter(javafx.event.ActionEvent.ACTION, ev -> downloadTask.cancel());

        Thread dlThread = new Thread(downloadTask, "updater-download");
        dlThread.setDaemon(true);
        dlThread.start();
    }

    // ── Apply update — dispatches to the right platform handler ──────────────

    private static void applyUpdate(Path downloadedFile) {
        String appImageEnv = System.getenv("APPIMAGE");
        String exePathStr  = System.getProperty("jpackage.app-path");

        if (appImageEnv != null) {
            // Running as a Linux AppImage — replace the AppImage file itself
            applyUpdateLinuxAppImage(downloadedFile, Path.of(appImageEnv));
        } else if (exePathStr != null && IS_WINDOWS) {
            // Running as a Windows jpackage EXE
            applyUpdateWindows(downloadedFile, Path.of(exePathStr));
        } else if (exePathStr != null) {
            // Running as a plain Linux jpackage app-image (not AppImage)
            applyUpdateLinuxJar(downloadedFile, Path.of(exePathStr));
        } else {
            // Dev mode
            new Alert(Alert.AlertType.INFORMATION,
                "Update downloaded to:\n" + downloadedFile +
                "\n\n(Running in dev mode — replace JAR manually.)",
                ButtonType.OK).showAndWait();
        }
    }

    // ── Windows updater: replaces JAR via .bat script ─────────────────────────

    private static void applyUpdateWindows(Path downloadedJar, Path exePath) {
        Path appDir  = exePath.getParent();
        Path jarPath = appDir.resolve("app").resolve("StockManager-1.0.0.jar");

        try {
            Path batFile = Files.createTempFile("sm-update-", ".bat");
            String nl   = "\r\n";
            String bat  =
                "@echo off" + nl +
                "rem Wait for the JVM process to exit fully" + nl +
                "ping -n 4 127.0.0.1 > nul" + nl +
                "copy /y \"" + downloadedJar.toAbsolutePath() + "\" \"" +
                    jarPath.toAbsolutePath() + "\"" + nl +
                "if errorlevel 1 (" + nl +
                "    echo Update failed: could not replace application file." + nl +
                "    pause" + nl +
                ") else (" + nl +
                "    explorer.exe \"" + exePath.toAbsolutePath() + "\"" + nl +
                ")" + nl +
                "del \"" + downloadedJar.toAbsolutePath() + "\" >nul 2>&1" + nl +
                "(goto) 2>nul & del \"%~f0\"" + nl;

            Files.writeString(batFile, bat);

            // Check if we have write permission to the target JAR.
            // If not, we must elevate the update process to Administrator.
            boolean needsElevation = false;
            try {
                // Attempt to open the target JAR for appending to check write permission
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(jarPath.toFile(), true)) {
                    // write access allowed
                }
            } catch (IOException e) {
                needsElevation = true;
            }

            if (needsElevation) {
                // Launch the batch script with elevated privileges (UAC prompt)
                new ProcessBuilder("powershell.exe", "-Command",
                    "Start-Process cmd.exe -ArgumentList '/c', '\"" + batFile.toAbsolutePath() + "\"' -Verb RunAs -WindowStyle Hidden")
                    .start();
            } else {
                // Launch normally (silently)
                new ProcessBuilder("cmd.exe", "/c", "start", "/min", "\"\"",
                                   batFile.toAbsolutePath().toString())
                    .start();
            }

            Platform.exit();
            System.exit(0);

        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR,
                "Could not apply update:\n" + ex.getMessage(),
                ButtonType.OK).showAndWait();
        }
    }

    // ── Linux AppImage updater: replaces the AppImage file via .sh script ─────

    private static void applyUpdateLinuxAppImage(Path downloadedAppImage, Path currentAppImage) {
        try {
            Path shFile = Files.createTempFile("sm-update-", ".sh");
            String sh =
                "#!/bin/bash\n" +
                "sleep 2\n" +
                "rm -f \"" + currentAppImage.toAbsolutePath() + "\"\n" +
                "cp -f \"" + downloadedAppImage.toAbsolutePath() + "\" \"" +
                    currentAppImage.toAbsolutePath() + "\"\n" +
                "chmod +x \"" + currentAppImage.toAbsolutePath() + "\"\n" +
                "if [ $? -eq 0 ]; then\n" +
                "    # Check if FUSE is missing on the system. If so, relaunch with extraction flag\n" +
                "    if [ ! -c /dev/fuse ] || ! (ldconfig -p 2>/dev/null | grep -q libfuse.so.2 || [ -f /lib/x86_64-linux-gnu/libfuse.so.2 ] || [ -f /usr/lib/libfuse.so.2 ] || [ -f /lib64/libfuse.so.2 ]); then\n" +
                "        \"" + currentAppImage.toAbsolutePath() + "\" --appimage-extract-and-run &\n" +
                "    else\n" +
                "        \"" + currentAppImage.toAbsolutePath() + "\" &\n" +
                "    fi\n" +
                "fi\n" +
                "rm -f \"" + downloadedAppImage.toAbsolutePath() + "\"\n" +
                "rm -f \"$0\"\n";

            Files.writeString(shFile, sh);
            shFile.toFile().setExecutable(true);

            new ProcessBuilder("bash", shFile.toAbsolutePath().toString()).start();

            Platform.exit();
            System.exit(0);

        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR,
                "Could not apply update:\n" + ex.getMessage(),
                ButtonType.OK).showAndWait();
        }
    }

    // ── Linux plain jpackage updater: replaces JAR via .sh script ────────────

    private static void applyUpdateLinuxJar(Path downloadedJar, Path exePath) {
        // jpackage app-image on Linux: bin/StockManager → ../lib/app/StockManager-1.0.0.jar
        Path appRoot = exePath.getParent().getParent();
        Path jarPath = appRoot.resolve("lib").resolve("app").resolve("StockManager-1.0.0.jar");

        try {
            Path shFile = Files.createTempFile("sm-update-", ".sh");
            String sh =
                "#!/bin/bash\n" +
                "sleep 2\n" +
                "rm -f \"" + jarPath.toAbsolutePath() + "\"\n" +
                "cp -f \"" + downloadedJar.toAbsolutePath() + "\" \"" +
                    jarPath.toAbsolutePath() + "\"\n" +
                "if [ $? -eq 0 ]; then\n" +
                "    \"" + exePath.toAbsolutePath() + "\" &\n" +
                "fi\n" +
                "rm -f \"" + downloadedJar.toAbsolutePath() + "\"\n" +
                "rm -f \"$0\"\n";

            Files.writeString(shFile, sh);
            shFile.toFile().setExecutable(true);

            new ProcessBuilder("bash", shFile.toAbsolutePath().toString()).start();

            Platform.exit();
            System.exit(0);

        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR,
                "Could not apply update:\n" + ex.getMessage(),
                ButtonType.OK).showAndWait();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String fetchUrl(String urlStr, int timeoutMs) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestProperty("User-Agent", "StockManager-Updater/1.0");
        if (conn.getResponseCode() != 200) return null;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    /** Extracts a string value from a simple flat JSON object. */
    private static String extractField(String json, String field) {
        Matcher m = Pattern.compile(
            "\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]+)\"")
            .matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Returns positive if v1 > v2, negative if v1 < v2, 0 if equal.
     * Handles "1.0.1" vs "1.0.0" etc.
     */
    private static int compareVersions(String v1, String v2) {
        String[] a = v1.split("\\.");
        String[] b = v2.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int pa = i < a.length ? parseIntSafe(a[i]) : 0;
            int pb = i < b.length ? parseIntSafe(b[i]) : 0;
            if (pa != pb) return Integer.compare(pa, pb);
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private static String fmt(long bytes) {
        if (bytes < 1024L)             return bytes + " B";
        if (bytes < 1024L * 1024)      return String.format("%.1f KB", bytes / 1024.0);
        return                                String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
