package com.example.javalsp.lsp.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. Apply CORS configuration
                .cors(cors -> cors.configurationSource(corsConfigSource()))
                // 2. Disable CSRF for WebSocket compatibility
                .csrf(csrf -> csrf.disable())
                // 3. Define authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Permit access to your WebSocket endpoint
                        .requestMatchers("/lsp").permitAll()
                        // Add other public endpoints if needed
                        .anyRequest().authenticated() // All other requests require authentication
                );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow credentials is important for WebSocket authentication scenarios
        configuration.setAllowCredentials(true);
        // In production, replace "*" with your frontend's specific origin (e.g.,
        // "http://localhost:5173")
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
