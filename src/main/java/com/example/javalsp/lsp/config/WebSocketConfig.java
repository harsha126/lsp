package com.example.javalsp.lsp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.example.javalsp.lsp.LspWebSocketHandler;
import com.example.javalsp.lsp.Process.LanguageServerProcessManager;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(
                lspWebSocketHandler(), "/lsp").addInterceptors(new QueryHandShakeInterceptor())
                .setAllowedOrigins("*");
    }

    @Bean
    public LspWebSocketHandler lspWebSocketHandler() {
        return new LspWebSocketHandler(languageServerProcessManager());
    }

    @Bean
    public LanguageServerProcessManager languageServerProcessManager() {
        return new LanguageServerProcessManager();
    }
}
