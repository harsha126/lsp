package com.example.javalsp.lsp;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.javalsp.lsp.Process.LanguageServerProcessTyped;
import com.example.javalsp.lsp.Process.LanguageServerProcess;
import com.example.javalsp.lsp.Process.LanguageServerProcessManager;

@Component
public class LspWebSocketHandler extends TextWebSocketHandler {

    private final LanguageServerProcessManager processManager;
    private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LspWebSocketHandler.class);

    public LspWebSocketHandler(LanguageServerProcessManager processManager) {
        this.processManager = processManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = extractUserId(session);
        String language = extractLanguage(session);
        System.out.println("Connection established for user: " + userId);

        sessionToUser.put(session.getId(), userId);
        System.out.println("Session to User Map: " + sessionToUser.toString());

        // Start or get existing LSP process for user
        processManager.getOrCreateProcess(userId, language, message -> {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                // Handle error
            }
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String userId = sessionToUser.get(session.getId());
        LanguageServerProcess process = processManager.getProcess(userId);
        logger.info("Received from editor: {}", message.toString());

        if (process != null) {
            process.sendMessage(message.getPayload());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = sessionToUser.remove(session.getId());
        processManager.cleanupUserSession(userId);
    }

    private String extractUserId(WebSocketSession session) {
        return "" + session.getAttributes().get("userId");
    }

    private String extractLanguage(WebSocketSession session) {
        return session.getAttributes().get("language").toString();
    }

}
