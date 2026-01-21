package com.be_ai_learning_platform.security;

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

    // ✅ US2 gate: check status từ DB để token cũ không dùng được khi bị block/unapproved
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {

        // ⭐ BỎ QUA PREFLIGHT
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        // ⭐ BỎ QUA AUTH + SELECT CLASS
        if (path.startsWith("/api/auth/") || path.equals("/api/users/select-class")) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        if (!jwtUtil.validateToken(token)) {
            chain.doFilter(request, response);
            return;
        }

        String email = jwtUtil.extractEmail(token);

        // ✅ US2: chỉ ACTIVE mới được vào hệ thống
        var userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty() || userOpt.get().getStatus() != UserStatus.ACTIVE) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            // Optional: response.getWriter().write("Account not approved or blocked");
            return;
        }

        List<String> roles = jwtUtil.extractRoles(token);

        // ✅ Fix incompatible types: cast về GrantedAuthority + Collectors.toList()
        List<GrantedAuthority> authorities = roles.stream()
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toList());

        var auth = new UsernamePasswordAuthenticationToken(email, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }
}
