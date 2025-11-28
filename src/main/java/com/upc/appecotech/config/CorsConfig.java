package com.upc.appecotech.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // Permitir credenciales
        config.setAllowCredentials(true);

        // Permitir Angular en puerto 4200
        config.setAllowedOrigins(Arrays.asList("http://localhost:4200"));

        // Permitir todos los headers
        config.setAllowedHeaders(Arrays.asList("*"));

        // Permitir todos los métodos HTTP (GET, POST, PUT, DELETE, OPTIONS)
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // Exponer headers (importante para JWT)
        config.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));

        // Tiempo de cache para preflight requests
        config.setMaxAge(3600L);

        // Aplicar configuración a todas las rutas /api/**
        source.registerCorsConfiguration("/api/**", config);

        return new CorsFilter(source);
    }
}