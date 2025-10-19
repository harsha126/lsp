package com.example.javalsp.lsp.config;

import java.net.URI;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

public class QueryHandShakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
            Map<String, Object> attributes) throws Exception {
        URI uri = request.getURI();
        String path = uri.getPath();

        if ("/lsp".equals(path)) {
            System.out.println("Handshake initiated for Java LSP endpoint.");
            attributes.put("language", "java");
        } else if ("/php".equals(path)) {
            System.out.println("Handshake initiated for PHP LSP endpoint.");
            attributes.put("language", "php");
        } else {
            response.setStatusCode(HttpStatus.NOT_FOUND);
            System.out.println("Unknown WebSocket path: " + path);
            return false;
        }

        Map<String, String> queryParams = UriComponentsBuilder.fromUri(uri).build().getQueryParams().toSingleValueMap();

        if (queryParams.containsKey("userId")) {
            attributes.put("userId", queryParams.get("userId"));
        } else {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        System.out.println(uri.getQuery().toString());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
            Exception exception) {

    }
}
