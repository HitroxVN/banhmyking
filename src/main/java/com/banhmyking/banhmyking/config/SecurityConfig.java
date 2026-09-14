package com.banhmyking.banhmyking.config;

import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.security.JwtAuthenticationFilter;
import com.banhmyking.banhmyking.security.JwtProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

/**
 * CSRF/session tắt luôn từ giờ: JWT stateless, không dùng cookie session.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }



    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/api/v1/health/**").permitAll()
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers("/api/v1/delivery/**").permitAll()
                        .requestMatchers("/api/v1/payments/webhook/**").permitAll()
                        // Admin user API: STAFF chỉ đọc, ADMIN toàn quyền
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/users").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/users/**").hasAnyRole("STAFF", "ADMIN")
                        // Admin orders: STAFF đọc, ADMIN toàn quyền (ghi)
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/orders/**").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // Shipper chỉ được đụng tới đơn được gán cho mình (service check ownership)
                        .requestMatchers("/api/v1/shipper/**").hasAnyRole("SHIPPER", "STAFF", "ADMIN")
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "Xác thực thất bại", ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, e) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                                        "Không có quyền truy cập", ErrorCode.FORBIDDEN)));

        return http.build();
    }

    /** Viết ErrorResponse JSON thủ công — tránh phụ thuộc ObjectMapper bean. */
    private static void writeError(HttpServletResponse response, int status, String message, ErrorCode code)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String timestamp = LocalDateTime.now().toString();
        String json = String.format(
                "{\"success\":false,\"message\":\"%s\",\"errorCode\":\"%s\",\"timestamp\":\"%s\"}",
                message, code.name(), timestamp);
        response.getWriter().write(json);
    }
}
