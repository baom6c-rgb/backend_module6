package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.FileExtractService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/api/student/materials")
@RequiredArgsConstructor
public class StudentMaterialController {

    private final FileExtractService fileExtractService;
    private final UserRepository userRepository;

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal String email) { // Đổi UserDetails thành String ở đây

        try {
            // Kiểm tra nếu email null (chưa login hoặc token sai)
            if (email == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Không tìm thấy thông tin đăng nhập");
            }

            // Vì Principal chỉ là String email, bạn cần tìm đối tượng User từ DB
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));

            // Gọi service xử lý trích xuất và lưu (US8, US9)
            fileExtractService.extractAndSave(file, user);

            return ResponseEntity.ok("Tải tài liệu và trích xuất thành công!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }
}