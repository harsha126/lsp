package com.example.javalsp.lsp.Process;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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
        // String workspacePath = "/workspaces/" + userId; // Isolated workspace per
        // user
        String userWorkspacePath = "/Users/harsha/projects/user-" + userId + "-workspace";
        // Path workspace = Paths.get("/workspace/user-feharshanew");
        // Files.createDirectories(workspace);

        String workspacePath = getWorkspacePath(userId);

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
                "-data", workspacePath);

        ensureWorkspaceDirectory(workspacePath);
        ensureWorkspaceDirectory(userWorkspacePath);
        Process process = processBuilder.start();

        return new LanguageServerProcess(
                process,
                messageHandler,
                userId);
    }

    private String getWorkspacePath(String userId) {
        // Option 1: Use temp directory (automatically cleaned up)
        String tempDir = System.getProperty("java.io.tmpdir");
        return tempDir + "/lsp-workspaces/" + userId;

        // Option 2: Use user's home directory
        // String homeDir = System.getProperty("user.home");
        // return homeDir + "/.lsp-workspaces/" + userId;
    }

    private void ensureWorkspaceDirectory(String workspacePath) throws IOException {
        java.io.File workspaceDir = new java.io.File(workspacePath);
        if (!workspaceDir.exists()) {
            if (!workspaceDir.mkdirs()) {
                throw new IOException("Failed to create workspace directory: " + workspacePath);
            }
        }

        // Verify we have write permissions
        if (!workspaceDir.canWrite()) {
            throw new IOException("No write permissions for workspace: " + workspacePath);
        }
    }

    public LanguageServerProcess getProcess(String userId) {
        return processes.get(userId);
    }

    public void scheduleCleanup(String userId) {
        // Implement timeout-based cleanup
        // e.g., remove process after 30 minutes of inactivity
    }
}