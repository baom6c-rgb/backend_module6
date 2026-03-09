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
@RequestMapping("/api/student/materials")
@RequiredArgsConstructor
public class StudentMaterialController {
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
            String cleaned = ensureMeaningfulText(raw);
            cleaned = programmingContentValidator.validateAndNormalize(cleaned);

            LearningMaterial material = new LearningMaterial();
            material.setUser(user);

            String title = req.getTitle() == null ? "" : req.getTitle().trim();
            material.setFileName(title.isBlank() ? "Pasted Text" : title);

            material.setFileType(FileType.TEXT);

            // optional: fileSize mô phỏng
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

            return ResponseEntity.ok(Map.of(
                    "materialId", material.getId(),
                    "fileName", material.getFileName(),
                    "text", text == null ? "" : text
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Lỗi: " + e.getMessage());
        }
    }

    /**
     * Rule-based gate để chặn input "lung tung" nhưng KHÔNG chặn prompt ngắn có nghĩa.
     * Mục tiêu: nếu người học nhập dạng "câu hỏi về hàm trong JS" thì vẫn tạo material bình thường.
     */
    private String ensureMeaningfulText(String input) {
        String t = input == null ? "" : input.trim();
        if (t.isEmpty()) {
            throw new IllegalArgumentException("Nội dung không được để trống");
        }

        // Nếu có dấu hiệu code/snippet thì coi như hợp lệ (nhiều trường hợp paste code ngắn).
        if (looksLikeCode(t)) {
            return t;
        }

        // Phải có ít nhất 1 ký tự chữ/số (tránh chỉ emoji/ký tự đặc biệt)
        boolean hasAlphaNum = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                hasAlphaNum = true;
                break;
            }
        }
        if (!hasAlphaNum) {
            throw new IllegalArgumentException("Nội dung bạn nhập không có chữ/số nên hệ thống không thể hiểu. Hãy nhập chủ đề rõ ràng (vd: 'câu hỏi về hàm trong JS').");
        }

        // Tỷ lệ ký tự lạ quá cao -> từ chối
        int weird = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isLetterOrDigit(c)
                    || Character.isWhitespace(c)
                    || c == '-' || c == '_' || c == '.' || c == ',' || c == '?' || c == '!' || c == ':' || c == ';' || c == '/' || c == '\\') {
                continue;
            }
            weird++;
        }
        if (weird > 0 && weird * 1.0 / Math.max(1, t.length()) > 0.45) {
            throw new IllegalArgumentException("Nội dung có quá nhiều ký tự lạ nên hệ thống không thể hiểu. Hãy nhập lại chủ đề rõ ràng.");
        }

        // Keyboard smash / lặp ký tự quá nhiều
        if (hasLongRepeatRun(t, 8)) {
            throw new IllegalArgumentException("Có vẻ bạn nhập nội dung không có nghĩa (lặp ký tự quá nhiều). Hãy nhập lại chủ đề rõ ràng.");
        }

        // Prompt ngắn vẫn OK, nhưng tối thiểu cần 2 "từ" có chữ/số để AI hiểu ý.
        // (vd: "hàm JS" vẫn OK)
        String[] parts = t.split("\\s+");
        int keywords = 0;
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            String cleaned = p.replaceAll("[^\\p{L}\\p{N}]", "");
            if (cleaned.length() >= 2) keywords++;
        }
        if (keywords < 2 && t.length() < 60) {
            throw new IllegalArgumentException("Nội dung chưa đủ rõ. Hãy nhập ít nhất 2 từ khoá (vd: 'hàm JS', 'array methods', 'Spring Security JWT').");
        }

        return t;
    }

    private boolean looksLikeCode(String text) {
        if (text == null) return false;
        String t = String.valueOf(text);
        boolean hasNewline = t.contains("\n");
        String[] hints = {"{", "}", ";", "=>", "function", "const ", "let ", "var ", "import ", "export ", "class ", "public ", "private ", "@"};
        boolean hit = false;
        for (String h : hints) {
            if (t.contains(h)) {
                hit = true;
                break;
            }
        }
        return hasNewline || hit;
    }

    private boolean hasLongRepeatRun(String s, int run) {
        if (s == null || s.isEmpty()) return false;
        int c = 1;
        for (int i = 1; i < s.length(); i++) {
            if (s.charAt(i) == s.charAt(i - 1)) {
                c++;
                if (c >= run) return true;
            } else {
                c = 1;
            }
        }
        return false;
    }
}