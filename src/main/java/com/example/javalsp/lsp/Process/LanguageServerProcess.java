package com.example.javalsp.lsp.Process;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LanguageServerProcess {
    private final Process process;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final BufferedReader errorReader;
    private final Thread readerThread;
    private final Thread errorReaderThread;
    private final String userId;
    private volatile boolean isReady = false;
    private volatile boolean isShuttingDown = false;
    private final List<String> pendingMessages = new CopyOnWriteArrayList<>();
    private static final Logger logger = LoggerFactory.getLogger(LanguageServerProcess.class);

    public LanguageServerProcess(Process process, Consumer<String> messageHandler, String userId) {
        this.process = process;
        this.userId = userId;
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        this.errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

        startProcessMonitor();

        this.errorReaderThread = new Thread(() -> {
            try {
                String line;
                boolean serverStarted = false;
                StringBuilder errorBuffer = new StringBuilder();

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
                        Thread.sleep(2000);

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

        // this.readerThread = new Thread(() -> {
        //     try {
        //         while (true) {
        //             // Read headers
        //             int contentLength = -1;
        //             String line;

        //             // Read all headers until we hit the empty line
        //             while ((line = reader.readLine()) != null) {
        //                 if (line.isEmpty()) {
        //                     break; // End of headers
        //                 }
        //                 if (line.startsWith("Content-Length:")) {
        //                     try {
        //                         contentLength = Integer.parseInt(line.substring(15).trim());
        //                     } catch (NumberFormatException e) {
        //                         logger.warn("Invalid Content-Length header: {}", line);
        //                     }
        //                 }
        //                 // Ignore other headers
        //             }

        //             if (line == null) {
        //                 break; // EOF
        //             }

        //             // If we couldn't read headers or no content-length, break
        //             if (contentLength > 0) {
        //                 char[] buffer = new char[contentLength];
        //                 int totalRead = 0;

        //                 while (totalRead < contentLength) {
        //                     int read = reader.read(buffer, totalRead, contentLength - totalRead);
        //                     if (read == -1) {
        //                         throw new IOException("Unexpected EOF while reading message body");
        //                     }
        //                     totalRead += read;
        //                 }

        //                 String message = new String(buffer, 0, totalRead);
        //                 logger.debug("Received LSP message for user {}: {}", userId, message);
        //                 messageHandler.accept(message);
        //             }

        //             // Read the JSON content
        //             char[] buffer = new char[contentLength];
        //             int totalRead = 0;

        //             while (totalRead < contentLength) {
        //                 int read = reader.read(buffer, totalRead, contentLength - totalRead);
        //                 if (read == -1) {
        //                     logger.error("Unexpected end of stream while reading content for user {}", userId);
        //                     break;
        //                 }
        //                 totalRead += read;
        //             }

        //             if (totalRead == contentLength) {
        //                 String jsonContent = new String(buffer, 0, totalRead);

        //                 // Validate JSON is complete
        //                 if (!jsonContent.trim().endsWith("}")) {
        //                     logger.error("Incomplete JSON message for user {}: expected {} chars, got {}, content: {}",
        //                             userId, contentLength, totalRead, jsonContent);
        //                 } else {
        //                     logger.info("LSP Response [{}]: {}", userId,
        //                             jsonContent.length() > 500 ? jsonContent.substring(0, 500) + "..." : jsonContent);
        //                     messageHandler.accept(jsonContent);
        //                 }
        //             } else {
        //                 logger.error("Failed to read complete message for user {}: expected {} chars, got {}",
        //                         userId, contentLength, totalRead);
        //             }
        //         }
        //     } catch (IOException e) {
        //         if (!isShuttingDown) {
        //             logger.error("Error reading LSP output for user {}: {}", userId, e.getMessage());
        //         }
        //     } catch (Exception e) {
        //         logger.error("Unexpected error in LSP reader thread for user {}: {}", userId, e.getMessage(), e);
        //     }

        //     logger.info("LSP reader thread exiting for user: {}", userId);
        // }, "LSP-Output-Reader-" + userId);
        this.readerThread = new Thread(() -> {
            try {
                int contentLength = 0;
                boolean readingHeaders = true;

                while (true) {
                    if (readingHeaders) {
                        String line = reader.readLine();
                        System.out.println("Read line: " + line);
                        if (line == null)
                            break;

                        if (line.isEmpty()) {
                            readingHeaders = false;
                            if (contentLength > 0) {
                                char[] buffer = new char[contentLength];
                                int totalRead = 0;
                                while (totalRead < contentLength) {
                                    int read = reader.read(buffer, totalRead, contentLength - totalRead);
                                    if (read == -1)
                                        break;
                                    totalRead += read;
                                }

                                String jsonContent = new String(buffer, 0, totalRead);
                                logger.info("LSP Response [{}]: {}", userId, jsonContent);

                                messageHandler.accept(jsonContent);

                                contentLength = 0;
                                readingHeaders = true;
                            }
                        } else if (line.startsWith("Content-Length:")) {
                            contentLength = Integer.parseInt(line.substring(15).trim());
                        } else if (line.startsWith("Content-Type:")) {
                            // Ignore content-type header
                        }
                    }
                }
            } catch (IOException e) {
                if (!isShuttingDown) {
                    logger.error("Error reading LSP output for user {}: {}", userId, e.getMessage());
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
                    case 13 ->
                        "PERMISSION DENIED or INITIALIZATION FAILURE - Check workspace permissions and initialization";
                    case 1 -> "GENERAL ERROR";
                    case 2 -> "MISUSE OF SHELL COMMAND";
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
        if (!isReady) {
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
            // logger.info(jsonMessage);

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
                logger.debug("Error closing writer: {}", e.getMessage());
            }

            try {
                reader.close();
            } catch (Exception e) {
                logger.debug("Error closing reader: {}", e.getMessage());
            }

            try {
                errorReader.close();
            } catch (Exception e) {
                logger.debug("Error closing errorReader: {}", e.getMessage());
            }

            process.destroyForcibly();

        } catch (Exception e) {
            logger.error("Error destroying LSP process for user {}: {}", userId, e.getMessage());
        }
    }
}