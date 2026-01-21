package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class AdminApproveController {

    private final UserRepository userRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @GetMapping("/admin/approve")
    public void approveUser(
            @RequestParam String token,
            HttpServletResponse response
    ) throws IOException {

        User user = userRepository.findByApproveToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid token"));

        if (user.getStatus() != UserStatus.WAITING_APPROVAL) {
            response.sendRedirect(
                    frontendUrl + "/approval-result?status=already-approved"
            );
            return;
        }

        user.setStatus(UserStatus.ACTIVE);
        user.setApproveToken(null);
        userRepository.save(user);

        response.sendRedirect(
                frontendUrl + "/approval-result?status=success"
        );
    }
}
