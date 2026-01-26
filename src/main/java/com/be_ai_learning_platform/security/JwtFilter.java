package com.be_ai_learning_platform.security;

import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        // public
        if (path.startsWith("/api/auth/")
                || path.startsWith("/uploads/")
                || path.equals("/api/users/status")
                || path.startsWith("/api/classes/")
                || path.startsWith("/api/modules/")
                || path.equals("/error")
        ) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        if (token.isBlank() || token.equals("null")) {
            chain.doFilter(request, response);
            return;
        }

        String email = jwtUtil.extractEmail(token);

        var userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        User user = userOpt.get();

        // ✅ set Authentication cho cả ACTIVE và WAITING_APPROVAL
        List<String> roles = jwtUtil.extractRoles(token);
        List<GrantedAuthority> authorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toList());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, authorities)
        );

        // ✅ chặn API nếu chưa ACTIVE (WAITING_APPROVAL chỉ được “đứng chờ”)
        if (user.getStatus() != UserStatus.ACTIVE) {
            // cho phép gọi các API “an toàn” nếu m cần sau này
            if (user.getStatus() == UserStatus.WAITING_APPROVAL) {
                // ví dụ: cho phép /api/users/me nếu cần hiển thị info
                if (path.equals("/api/users/me") || path.equals("/api/users/status")) {
                    chain.doFilter(request, response);
                    return;
                }
            }
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        chain.doFilter(request, response);
    }
}
