package com.example.javalsp.lsp.Process;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LanguageServerProcess {
    private final Process process;
    private final BufferedWriter writer;
    private final BufferedReader errorReader;
    private final Thread readerThread;
    private final Thread errorReaderThread;
    private final String userId;
    private volatile boolean isReady = false;
    private volatile boolean isShuttingDown = false;
    private final List<String> pendingMessages = new CopyOnWriteArrayList<>();
    private static final Logger logger = LoggerFactory.getLogger(LanguageServerProcess.class);

    public LanguageServerProcess(Process process,String lang,Consumer<String> messageHandler, String userId) {
        this.process = process;
        this.userId = userId;
        // Correctly specify UTF-8 encoding for all readers and writers
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));

        startProcessMonitor();

        this.errorReaderThread = new Thread(() -> {
            try {
                String line;
                boolean serverStarted = false;
                StringBuilder errorBuffer = new StringBuilder();
                if(lang == "php"){
                    Thread.sleep(2000);
                    markAsReady();
                } 
                while ((line = errorReader.readLine()) != null) {
                    logger.error("LSP Error [{}]: {}", userId, line);
                    errorBuffer.append(line).append("\n");

                    if (line.contains("Exception") || line.contains("Error") ||
                            line.contains("Failed") || line.contains("Cannot")) {
                        logger.error("CRITICAL LSP Error [{}]: {}", userId, line);
                    }

                    if (!serverStarted && (line.contains("Main thread is waiting") ||
                            line.contains("OpenJDK 64-Bit Server VM warning") ||
                            line.contains("Started language server"))) {
                        serverStarted = true;
                        logger.info("LSP Server startup detected for user: {}", userId);
                        Thread.sleep(2000); // Allow server time to initialize fully

                        markAsReady();
                    }
                }

                if (errorBuffer.length() > 0) {
                    logger.error("Final LSP stderr dump for user {}: \n{}", userId, errorBuffer.toString());
                }
            } catch (IOException e) {
                if (!isShuttingDown) {
                    logger.error("Error reading stderr for user {}: {}", userId, e.getMessage());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "LSP-Error-Reader-" + userId);
        errorReaderThread.setDaemon(true);
        errorReaderThread.start();

        // **FIXED**: The reader thread now correctly processes the LSP byte stream.
        // It reads headers and then reads the exact number of bytes specified by
        // Content-Length.
        this.readerThread = new Thread(() -> {
            final InputStream inputStream = process.getInputStream();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    ByteArrayOutputStream headerStream = new ByteArrayOutputStream();
                    int b;
                    byte[] sequence = new byte[4];
                    int seqPtr = 0;

                    while ((b = inputStream.read()) != -1) {
                        headerStream.write(b);
                        sequence[seqPtr % 4] = (byte) b;
                        seqPtr++;
                        if (seqPtr >= 4 &&
                                sequence[(seqPtr - 4) % 4] == '\r' &&
                                sequence[(seqPtr - 3) % 4] == '\n' &&
                                sequence[(seqPtr - 2) % 4] == '\r' &&
                                sequence[(seqPtr - 1) % 4] == '\n') {
                            break;
                        }
                    }

                    if (b == -1) {
                        break; 
                    }

                    String headers = new String(headerStream.toByteArray(), StandardCharsets.UTF_8);

                    int contentLength = -1;
                    for (String line : headers.split("\r\n")) {
                        if (line.startsWith("Content-Length: ")) {
                            try {
                                contentLength = Integer.parseInt(line.substring("Content-Length: ".length()).trim());
                            } catch (NumberFormatException e) {
                                logger.error("Failed to parse Content-Length for user {}: {}", userId, line);
                                contentLength = -1;
                            }
                            break;
                        }
                    }

                    if (contentLength == -1) {
                        logger.warn("LSP message received without a valid Content-Length header for user {}", userId);
                        continue;
                    }

                    byte[] contentBytes = new byte[contentLength];
                    int totalBytesRead = 0;
                    while (totalBytesRead < contentLength) {
                        int bytesRead = inputStream.read(contentBytes, totalBytesRead, contentLength - totalBytesRead);
                        if (bytesRead == -1) {
                            throw new IOException("Stream ended prematurely while reading content for user " + userId);
                        }
                        totalBytesRead += bytesRead;
                    }

                    String content = new String(contentBytes, StandardCharsets.UTF_8);

                    logger.debug("LSP -> Monaco [{}]: {}", userId, content);
                    messageHandler.accept(content);
                }
            } catch (IOException e) {
                if (!isShuttingDown) {
                    logger.error("Error reading from LSP process for user {}: {}", userId, e.getMessage());
                }
            } catch (Exception e) {
                if (!isShuttingDown) {
                    logger.error("Unexpected error in LSP reader thread for user {}: {}", userId, e.getMessage(), e);
                }
            }
        }, "LSP-Output-Reader-" + userId);

        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void startProcessMonitor() {
        Thread monitor = new Thread(() -> {
            try {
                int exitCode = process.waitFor();
                isReady = false;

                String exitMessage = switch (exitCode) {
                    case 1 -> "GENERAL ERROR";
                    case 2 -> "MISUSE OF SHELL COMMAND";
                    case 13 ->
                        "PERMISSION DENIED or INITIALIZATION FAILURE - Check workspace permissions and initialization";
                    case 126 -> "COMMAND CANNOT EXECUTE";
                    case 127 -> "COMMAND NOT FOUND";
                    default -> "UNKNOWN ERROR";
                };

                logger.error("LSP process for user {} terminated with exit code {}: {}",
                        userId, exitCode, exitMessage);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "LSP-Process-Monitor-" + userId);
        monitor.setDaemon(true);
        monitor.start();
    }

    private synchronized void markAsReady() {
        if (isReady || isShuttingDown) {
            return;
        }

        if (!process.isAlive()) {
            logger.error("Cannot mark LSP as ready for user {} - process is not alive", userId);
            pendingMessages.clear();
            return;
        }

        try {
            Thread.sleep(1000);

            if (!process.isAlive()) {
                logger.error("LSP process died during ready initialization for user {}", userId);
                pendingMessages.clear();
                return;
            }

            isReady = true;
            logger.info("LSP Server READY for user: {}", userId);

            List<String> messagesToSend = List.copyOf(pendingMessages);
            pendingMessages.clear();

            for (String msg : messagesToSend) {
                sendMessageInternal(msg);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while marking LSP ready for user {}", userId);
        }
    }

    public void sendMessage(String jsonMessage) {
        if (!isReady()) {
            logger.info("LSP not ready yet for user {}, queueing message", userId);
            pendingMessages.add(jsonMessage);
            return;
        }
        sendMessageInternal(jsonMessage);
    }

    private void sendMessageInternal(String jsonMessage) {
        if (!process.isAlive()) {
            logger.error("Cannot send message to LSP for user {} - process is not alive", userId);
            isReady = false;
            return;
        }

        if (isShuttingDown) {
            logger.warn("Cannot send message to LSP for user {} - shutting down", userId);
            return;
        }

        try {
            logger.info("LSP Request [{}]: {}", userId,
                    jsonMessage.length() > 200 ? jsonMessage.substring(0, 200) + "..." : jsonMessage);

            byte[] contentBytes = jsonMessage.getBytes(StandardCharsets.UTF_8);
            int contentLength = contentBytes.length;

            String headers = "Content-Length: " + contentLength + "\r\n" +
                    "Content-Type: application/vscode-jsonrpc; charset=utf-8\r\n" +
                    "\r\n";

            synchronized (writer) {
                writer.write(headers);
                writer.write(jsonMessage);
                writer.flush();
            }

            logger.info("Sent message to LSP [{}], length: {}", userId, contentLength);
        } catch (IOException e) {
            logger.error("Error sending message to LSP for user {}: {}", userId, e.getMessage(), e);
            isReady = false;

            if (!process.isAlive()) {
                logger.error("LSP process died for user {}", userId);
            }
        }
    }

    public boolean isReady() {
        return isReady && process.isAlive();
    }

    public void destroy() {
        isShuttingDown = true;
        isReady = false;

        try {
            logger.info("Destroying LSP process for user: {}", userId);

            pendingMessages.clear();

            readerThread.interrupt();
            errorReaderThread.interrupt();

            try {
                writer.close();
            } catch (Exception e) {
                logger.debug("Error closing writer for user {}: {}", userId, e.getMessage());
            }


            try {
                errorReader.close();
            } catch (Exception e) {
                logger.debug("Error closing errorReader for user {}: {}", userId, e.getMessage());
            }

            process.destroyForcibly();

        } catch (Exception e) {
            logger.error("Error destroying LSP process for user {}: {}", userId, e.getMessage());
        }
    }
}