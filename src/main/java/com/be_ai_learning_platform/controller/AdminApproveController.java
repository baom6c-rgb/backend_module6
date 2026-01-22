package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class AdminApproveController {

    private final UserRepository userRepository;
    private final UserService userService;

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

        // ✅ GỌI SERVICE (approve + gửi mail)
        userService.approveUser(user.getId());

        response.sendRedirect(
                frontendUrl + "/approval-result?status=success"
        );
    }
}
