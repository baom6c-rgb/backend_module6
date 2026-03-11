package com.be_ai_learning_platform.config;

import com.be_ai_learning_platform.security.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // ✅ CORS — trỏ đúng vào bean corsConfigurationSource
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // ❌ CSRF OFF (API)
                .csrf(csrf -> csrf.disable())

                // ❌ STATELESS
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 🔐 AUTH RULES
                .authorizeHttpRequests(auth -> auth

                        // ⭐ PREFLIGHT
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ⭐ PUBLIC STATIC FILES (AVATAR)
                        .requestMatchers("/uploads/**").permitAll()

                        // ⭐ PUBLIC APIs
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/classes/**",
                                "/api/users/status",
                                "/api/modules/**",
                                "/admin/approve",
                                "/error"
                        ).permitAll()

                        // 🔐 ADMIN
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/exam-attempts/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/exam-attempts/**").authenticated()

                        // 🔐 STUDENT
                        .requestMatchers("/api/student/**").hasRole("STUDENT")
                        .requestMatchers("/api/exam-attempts/my-attempts").authenticated()

                        // 🔐 USER (login rồi)
                        .requestMatchers(
                                "/api/users/me",
                                "/api/users/me/**"
                        ).authenticated()

                        // 🔐 DEFAULT
                        .anyRequest().authenticated()
                )

                // 🔐 JWT FILTER
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}