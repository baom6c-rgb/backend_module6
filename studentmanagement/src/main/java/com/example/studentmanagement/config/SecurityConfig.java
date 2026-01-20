package com.example.studentmanagement.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtFilter jwtFilter;

    // ================= SECURITY FILTER =================
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        return http
                // ❌ CSRF không cần cho JWT
                .csrf(csrf -> csrf.disable())

                // ✅ DÙNG CORS TỪ WebMvcConfigurer
                .cors(Customizer.withDefaults())

                // ❌ KHÔNG DÙNG SESSION
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authorizeHttpRequests(auth -> auth

                        // 🔥 PRE-FLIGHT
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ========== PUBLIC ==========
                        .requestMatchers(
                                "/api/auth/**",        // login, register, google
                                "/api/classes/**"      // load danh sách lớp
                        ).permitAll()

                        // ========== STUDENT ==========
                        .requestMatchers(HttpMethod.POST,
                                "/api/users/select-class"
                        ).hasRole("STUDENT")

                        // ========== ADMIN (ví dụ) ==========
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // ========== OTHERS ==========
                        .anyRequest().authenticated()
                )

                // nếu cần test nhanh (không bắt buộc)
                .httpBasic(Customizer.withDefaults())

                // JWT FILTER
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

                .build();
    }

    // ================= AUTH PROVIDER =================
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setPasswordEncoder(passwordEncoder());
        provider.setUserDetailsService(userDetailsService);
        return provider;
    }

    // ================= AUTH MANAGER =================
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config
    ) throws Exception {
        return config.getAuthenticationManager();
    }

    // ================= PASSWORD =================
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    // ================= CORS =================
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")

                        // FE PORT (vite)
                        .allowedOrigins(
                                "http://localhost:5173",
                                "http://localhost:5175"
                        )

                        .allowedMethods(
                                "GET", "POST", "PUT", "DELETE", "OPTIONS"
                        )

                        .allowedHeaders("*")

                        // 🔥 CHO PHÉP FE ĐỌC HEADER Authorization
                        .exposedHeaders("Authorization")

                        .allowCredentials(true);
            }
        };
    }
}
