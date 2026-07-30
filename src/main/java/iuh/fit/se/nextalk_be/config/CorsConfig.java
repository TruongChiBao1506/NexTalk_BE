package iuh.fit.se.nextalk_be.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:3001}")
    private List<String> allowedOrigins;

    @Value("${RENDER_EXTERNAL_URL:}")
    private String renderExternalUrl;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration apiConfig = createConfiguration(allowedOrigins);
        CorsConfiguration websocketConfig = createConfiguration(
                OriginAllowlist.merge(allowedOrigins, renderExternalUrl));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/ws/**", websocketConfig);
        source.registerCorsConfiguration("/ws-raw", websocketConfig);
        source.registerCorsConfiguration("/ws-raw/**", websocketConfig);
        source.registerCorsConfiguration("/**", apiConfig);
        return source;
    }

    private CorsConfiguration createConfiguration(List<String> origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With",
                "X-Client-Platform"
        ));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        return config;
    }
}
