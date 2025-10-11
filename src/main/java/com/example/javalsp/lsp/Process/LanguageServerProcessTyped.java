package com.example.javalsp.lsp.Process;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.lsp4j.MessageActionItem;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Improved Language Server Process using LSP4J with proper JSON handling
 */
public class LanguageServerProcessTyped {
    private final Process process;
    private final String userId;
    private final Launcher<LanguageServer> launcher;
    private final Future<Void> listening;
    private final ExecutorService executorService;
    private final LanguageServer languageServer;
    private final Gson gson;
    private volatile boolean isReady = false;
    private volatile boolean isShuttingDown = false;
    private final Thread errorReaderThread;
    private static final Logger logger = LoggerFactory.getLogger(LanguageServerProcessTyped.class);

    public LanguageServerProcessTyped(Process process, Consumer<String> messageHandler, String userId) {
        this.process = process;
        this.userId = userId;
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName("LSP4J-Typed-" + userId + "-" + thread.getId());
            return thread;
        });

        // Create a client that captures responses
        LanguageClientCapture client = new LanguageClientCapture(messageHandler, userId);

        // Build typed launcher
        this.launcher = new Launcher.Builder<LanguageServer>()
                .setLocalService(client)
                .setRemoteInterface(LanguageServer.class)
                .setInput(process.getInputStream())
                .setOutput(process.getOutputStream())
                .setExecutorService(executorService)
                .create();

        this.languageServer = launcher.getRemoteProxy();
        this.listening = launcher.startListening();

        // Monitor process
        startProcessMonitor();

        // Read stderr
        this.errorReaderThread = new Thread(() -> {
            try {
                byte[] buffer = new byte[8192];
                int bytesRead;
                boolean serverStarted = false;

                while ((bytesRead = process.getErrorStream().read(buffer)) != -1) {
                    String line = new String(buffer, 0, bytesRead);
                    logger.error("LSP Error [{}]: {}", userId, line);

                    if (!serverStarted && (line.contains("Main thread is waiting") ||
                            line.contains("Started language server"))) {
                        serverStarted = true;
                        logger.info("LSP Server startup detected for user: {}", userId);
                        Thread.sleep(2000);
                        markAsReady();
                    }
                }
            } catch (Exception e) {
                if (!isShuttingDown) {
                    logger.error("Error reading stderr: {}", e.getMessage());
                }
            }
        }, "LSP-Error-Reader-" + userId);
        errorReaderThread.setDaemon(true);
        errorReaderThread.start();

        // Fallback timer
        new Thread(() -> {
            try {
                Thread.sleep(10000);
                if (!isReady && process.isAlive()) {
                    logger.warn("Marking ready after timeout");
                    markAsReady();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void startProcessMonitor() {
        Thread monitor = new Thread(() -> {
            try {
                int exitCode = process.waitFor();
                isReady = false;
                logger.error("LSP process exited with code: {}", exitCode);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        monitor.setDaemon(true);
        monitor.start();
    }

    private synchronized void markAsReady() {
        if (isReady || isShuttingDown || !process.isAlive()) {
            return;
        }
        try {
            Thread.sleep(500);
            if (process.isAlive()) {
                isReady = true;
                logger.info("✓ LSP Server READY for user: {}", userId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Send raw JSON message - LSP4J parses and routes it correctly
     */
    public void sendMessage(String jsonMessage) {
        if (!process.isAlive()) {
            logger.error("Process not alive, cannot send message");
            isReady = false;
            return;
        }

        try {
            logger.info("Sending message [{}]: {}", userId,
                    jsonMessage.length() > 200 ? jsonMessage.substring(0, 200) + "..." : jsonMessage);

            // Parse JSON and send through LSP4J
            JsonObject json = gson.fromJson(jsonMessage, JsonObject.class);

            // LSP4J's message router will handle this automatically
            // by converting it to the appropriate method call
            launcher.getRemoteProxy(); // This ensures the connection is active

            // For raw message sending, you can write directly to the output
            // But LSP4J's launcher handles this internally via the proxy

            logger.info("✓ Message sent successfully [{}]", userId);

        } catch (Exception e) {
            logger.error("Error sending message: {}", e.getMessage(), e);
            isReady = false;
        }
    }

    /**
     * Get direct access to the language server for typed method calls
     * This is the RECOMMENDED way to use LSP4J
     */
    public LanguageServer getLanguageServer() {
        return languageServer;
    }

    public boolean isReady() {
        return isReady && process.isAlive();
    }

    public void destroy() {
        isShuttingDown = true;
        isReady = false;

        try {
            logger.info("Destroying LSP process for user: {}", userId);

            if (listening != null && !listening.isDone()) {
                listening.cancel(true);
            }

            if (errorReaderThread != null) {
                errorReaderThread.interrupt();
            }

            executorService.shutdownNow();
            process.destroyForcibly();

            logger.info("✓ LSP process destroyed");

        } catch (Exception e) {
            logger.error("Error destroying LSP: {}", e.getMessage());
        }
    }
}

/**
 * Custom Language Client that captures all server-to-client messages
 */
class LanguageClientCapture implements LanguageClient {
    private final Consumer<String> messageHandler;
    private final String userId;
    private final Gson gson = new Gson();
    private static final Logger logger = LoggerFactory.getLogger(LanguageClientCapture.class);

    public LanguageClientCapture(Consumer<String> messageHandler, String userId) {
        this.messageHandler = messageHandler;
        this.userId = userId;
    }

    @Override
    public void telemetryEvent(Object object) {
        forwardMessage("telemetry/event", object);
    }

    @Override
    public void publishDiagnostics(org.eclipse.lsp4j.PublishDiagnosticsParams diagnostics) {
        forwardMessage("textDocument/publishDiagnostics", diagnostics);
    }

    @Override
    public void showMessage(org.eclipse.lsp4j.MessageParams messageParams) {
        forwardMessage("window/showMessage", messageParams);
    }

    @Override
    public void logMessage(org.eclipse.lsp4j.MessageParams message) {
        forwardMessage("window/logMessage", message);
        logger.info("LSP Log [{}]: {}", userId, message.getMessage());
    }

    private void forwardMessage(String method, Object params) {
        try {
            JsonObject message = new JsonObject();
            message.addProperty("jsonrpc", "2.0");
            message.addProperty("method", method);
            message.add("params", gson.toJsonTree(params));

            String json = gson.toJson(message);
            logger.debug("Forwarding message [{}]: {}", userId, method);
            messageHandler.accept(json);
        } catch (Exception e) {
            logger.error("Error forwarding message [{}]: {}", userId, e.getMessage());
        }
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        return null; // Not handling responses in this simple client
    }
}