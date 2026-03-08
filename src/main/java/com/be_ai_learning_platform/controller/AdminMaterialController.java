package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.CreateTextMaterialRequest;
import com.be_ai_learning_platform.entity.LearningMaterial;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.FileType;
import com.be_ai_learning_platform.entity.enums.MaterialStatus;
import com.be_ai_learning_platform.repository.LearningMaterialRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.FileExtractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.be_ai_learning_platform.service.validator.ProgrammingContentValidator;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/materials")
@RequiredArgsConstructor
public class AdminMaterialController {

    private static final int MAX_TEXT_CHARS = 20000;

    private final FileExtractService fileExtractService;
    private final UserRepository userRepository;
    private final LearningMaterialRepository learningMaterialRepository;
    private final ProgrammingContentValidator programmingContentValidator;

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

    @PostMapping("/text")
    public ResponseEntity<?> createFromText(
            @Valid @RequestBody CreateTextMaterialRequest req,
            @AuthenticationPrincipal String email
    ) {
        try {
            if (email == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Không tìm thấy thông tin đăng nhập");
            }

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));

            String raw = req.getRawText() == null ? "" : req.getRawText().trim();
            if (raw.isBlank()) {
                return ResponseEntity.badRequest().body("Nội dung không được để trống");
            }

            if (raw.length() > MAX_TEXT_CHARS) {
                return ResponseEntity.badRequest()
                        .body("Nội dung quá dài (tối đa " + MAX_TEXT_CHARS + " ký tự). Hãy rút gọn hoặc chia nhỏ nội dung");
            }

            String cleaned = programmingContentValidator.validateAndNormalize(raw);

            LearningMaterial material = new LearningMaterial();
            material.setUser(user);

            String title = req.getTitle() == null ? "" : req.getTitle().trim();
            material.setFileName(title.isBlank() ? "Admin Pasted Text" : title);
            material.setFileType(FileType.TEXT);
            material.setFileSize((long) cleaned.length());
            material.setExtractedText(cleaned);
            material.setStatus(MaterialStatus.EXTRACTED);

            LearningMaterial saved = learningMaterialRepository.save(material);

            return ResponseEntity.ok(Map.of(
                    "materialId", saved.getId(),
                    "message", "Tạo học liệu từ văn bản thành công!"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Lỗi: " + e.getMessage());
        }
    }
}
