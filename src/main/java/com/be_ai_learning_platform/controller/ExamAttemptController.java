package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.UserExamAttemptDTO;
import com.be_ai_learning_platform.entity.ExamAttempt;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.ExamAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exam-attempts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Hỗ trợ gọi từ React/Vite
public class ExamAttemptController {

    private final ExamAttemptService attemptService;

    private final UserRepository userRepository;

    /**
     * FE: getMyExamAttemptsApi()
     * Lấy danh sách lịch sử thi của user để hiển thị bảng Review
     */
    @GetMapping("/my-attempts")
    public ResponseEntity<List<UserExamAttemptDTO>> getMyAttempts(
            Authentication authentication) {  // ← Lấy từ JWT

        // Lấy email từ token
        String email = authentication.getName();

        // Tìm user từ email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long currentUserId = user.getId();  // ← ID THẬT của user đang login

        return ResponseEntity.ok(attemptService.getMyAttempts(currentUserId));
    }

    /**
     * FE: getExamAttemptsStatsApi()
     * Lấy dữ liệu cho các thẻ thống kê (Tổng bài thi, Điểm TB, Xếp hạng)
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Long currentUserId = 1L;
        return ResponseEntity.ok(attemptService.getExamStats(currentUserId));
    }

    /**
     * FE: getExamAttemptByIdApi(id)
     * Xem chi tiết một bài thi cụ thể (dùng cho modal popup)
     */
    @GetMapping("/{id}")
    public ResponseEntity<ExamAttempt> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(attemptService.getAttemptById(id));
    }

    /**
     * Dành cho ADMIN: Xem toàn bộ lịch sử hệ thống
     */
    @GetMapping("/admin/all-attempts")
    @PreAuthorize("hasRole('ADMIN')") // Bảo mật thêm ở tầng method
    public ResponseEntity<List<UserExamAttemptDTO>> getAllAttempts() {
        return ResponseEntity.ok(attemptService.getAllAttemptsForAdmin());
    }

    /**
     * FE: startExamAttemptApi(examId)
     * Body format: { "examId": 123 }
     */
    @PostMapping("/start")
    public ResponseEntity<ExamAttempt> startExam(@RequestBody Map<String, Long> request) {
        Long currentUserId = 1L;
        Long examId = request.get("examId");
        return ResponseEntity.ok(attemptService.startExam(currentUserId, examId));
    }

    /**
     * FE: submitExamAttemptApi(id, answers)
     * Body format: { "answers": [...] }
     */
    @PostMapping("/{id}/submit")
    public ResponseEntity<ExamAttempt> submitExam(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        Object answers = payload.get("answers");
        return ResponseEntity.ok(attemptService.submitExam(id, answers));
    }
}