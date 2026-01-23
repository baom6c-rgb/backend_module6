package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.entity.LearningMaterial;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.repository.LearningMaterialRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.FileExtractService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/student/materials")
@RequiredArgsConstructor
public class StudentMaterialController {

    private final FileExtractService fileExtractService;
    private final UserRepository userRepository;
    private final LearningMaterialRepository learningMaterialRepository;

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal String email
    ) {
        try {
            if (email == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Không tìm thấy thông tin đăng nhập");
            }

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));

            // ✅ Service mới nên trả materialId (như bản tao rewrite)
            Long materialId = fileExtractService.extractAndSave(file, user);

            return ResponseEntity.ok(Map.of(
                    "materialId", materialId,
                    "message", "Tải tài liệu và trích xuất thành công!"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Lỗi: " + e.getMessage());
        }
    }

    // ✅ US9: đọc lại văn bản đã trích xuất
    @GetMapping("/{id}/text")
    public ResponseEntity<?> getExtractedText(
            @PathVariable Long id,
            @AuthenticationPrincipal String email
    ) {
        try {
            if (email == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Không tìm thấy thông tin đăng nhập");
            }

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));

            LearningMaterial material = learningMaterialRepository.findByIdAndUser(id, user)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy tài liệu"));

            String text = material.getExtractedText();
            if (text == null || text.isBlank()) {
                return ResponseEntity.badRequest().body("Tài liệu chưa trích xuất xong hoặc không có nội dung");
            }

            return ResponseEntity.ok(text);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }
}
