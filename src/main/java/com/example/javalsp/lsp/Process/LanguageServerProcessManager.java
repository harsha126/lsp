package com.example.javalsp.lsp.Process;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

@Component
public class LanguageServerProcessManager {

    private final Map<String, LanguageServerProcess> processes = new ConcurrentHashMap<>();
    private final String jdtLsPath = "/Users/harsha/Downloads/jdt-language-server-1.51.0-202509051440";

    public LanguageServerProcess getOrCreateProcess(String userId, Consumer<String> messageHandler) {
        return processes.computeIfAbsent(userId, id -> {
            try {
                return startLanguageServerProcess(id, messageHandler);
            } catch (IOException e) {
                throw new RuntimeException("Failed to start LSP for user: " + id, e);
            }
        });
    }

    private LanguageServerProcess startLanguageServerProcess(String userId, Consumer<String> messageHandler)
            throws IOException {
        System.out.println("Starting LSP for user: " + userId);
        String userWorkspacePath = getUserWorkspacePath(userId);
        ensureWorkspaceDirectory(userWorkspacePath);

        Path projectDirectory = Paths.get(userWorkspacePath, "project");
        if (!Files.exists(projectDirectory)) {
            Files.createDirectories(projectDirectory);
            System.out.println("Created project subdirectory: " + projectDirectory);
        }

        ProcessBuilder processBuilder = new ProcessBuilder(
                "java",
                "-Declipse.application=org.eclipse.jdt.ls.core.id1",
                "-Dosgi.bundles.defaultStartLevel=4",
                "-Declipse.product=org.eclipse.jdt.ls.core.product",
                "-Dlog.level=ALL",
                "-noverify",
                "-Xmx1G",
                "-jar", jdtLsPath + "/plugins/org.eclipse.equinox.launcher_1.7.0.v20250519-0528.jar",
                "-configuration", jdtLsPath + "/config_mac",
                "-data", userWorkspacePath);

        Process process = processBuilder.start();
        System.out.println("LSP process started for user " + userId + " with PID: " + process.pid());

        return new LanguageServerProcess(
                process,
                messageHandler,
                userId);
    }

    public void cleanupUserSession(String userId) {
        System.out.println(
                "fddfdsafdsfdfdfdsfdfdafdsfdrewfewacewacdsfadfewafcesafefacdsafdsafdsfdsafdsfdsfdsfdsafdfdfdfdsafdsafdsfdsafdsafdsafdsafdsafdsafdsafdsafdsafdsafdsfdsafdsafdsafdsafdsafdsafdsafdsafd");
        if (userId == null || userId.isBlank()) {
            System.err.println("Cannot cleanup session for a null or empty userId.");
            return;
        }

        LanguageServerProcess process = processes.remove(userId);
        if (process != null) {
            System.out.println("Stopping LSP process for user: " + userId);
            process.destroy();
        } else {
            System.out.println("No running LSP process found for user: " + userId);
        }

        String userWorkspacePath = getUserWorkspacePath(userId);
        Path workspacePath = Paths.get(userWorkspacePath);
        try {
            if (Files.exists(workspacePath)) {
                System.out.println("Deleting workspace directory for user " + userId + ": " + userWorkspacePath);
                // Walk the directory tree and delete files and folders from the inside out
                try (Stream<Path> walk = Files.walk(workspacePath)) {
                    walk.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
                System.out.println("Successfully deleted workspace for user: " + userId);
            }
        } catch (IOException e) {
            System.err.println("Failed to delete workspace directory for user " + userId + " at " + userWorkspacePath);
            e.printStackTrace();
        }
    }

    private String getUserWorkspacePath(String userId) {
        return "/opt/lsp-workspace/user-" + userId + "-workspace";
    }

    private void ensureWorkspaceDirectory(String workspacePath) throws IOException {
        java.io.File workspaceDir = new java.io.File(workspacePath);
        if (!workspaceDir.exists()) {
            if (!workspaceDir.mkdirs()) {
                throw new IOException("Failed to create workspace directory: " + workspacePath);
            }
        }

        if (!workspaceDir.canWrite()) {
            throw new IOException("No write permissions for workspace: " + workspacePath);
        }
    }

    public LanguageServerProcess getProcess(String userId) {
        return processes.get(userId);
    }
}