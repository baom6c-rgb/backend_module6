package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.UserDashboardStatsDTO;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5175")
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserRepository userRepository;

    /**
     * Lấy thống kê dashboard của học viên
     * GET /api/users/dashboard/stats
     */
    @GetMapping("/dashboard/stats")
    public ResponseEntity<UserDashboardStatsDTO> getDashboardStats() {
        try {
            // 1. Lấy email từ JWT token
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = authentication.getName();

            // 2. Tìm User trong DB để lấy ID (Vì Service cần Long userId)
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found: " + email));

            // 3. Gọi service bằng ID (Long) - FIX LỖI String vs Long
            UserDashboardStatsDTO stats = dashboardService.getStats(user.getId());

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            e.printStackTrace();
            // Nếu lỗi, trả về một Object rỗng hoặc thông báo lỗi thay vì tạo Mock sai class
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Endpoint nhận userId từ request param (Dùng cho FE gọi trực tiếp bằng ID)
     * GET /api/users/dashboard/stats-by-id?userId=1
     */
    @GetMapping("/dashboard/stats-by-id")
    public ResponseEntity<UserDashboardStatsDTO> getDashboardStatsByUserId(@RequestParam Long userId) {
        try {
            // Gọi trực tiếp bằng Long userId
            UserDashboardStatsDTO stats = dashboardService.getStats(userId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}