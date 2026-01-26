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

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // ✅ CORS
                .cors(cors -> {})

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
                                "/api/test/ai/**",
                                "/error"
                        ).permitAll()

                        // 🔐 ADMIN
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // 🔐 STUDENT
                        .requestMatchers("/api/student/**").hasRole("STUDENT")

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
