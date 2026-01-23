package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.service.AvatarStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AvatarStorageServiceImpl implements AvatarStorageService {

    private static final long MAX_BYTES = 2L * 1024 * 1024; // 2MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );

    @Value("${app.upload.avatar-dir:uploads/avatars}")
    private String avatarDir;

    @Value("${app.public.base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @Override
    public String storeAvatar(MultipartFile file, Long userId) {
        validate(file);

        String original = file.getOriginalFilename();
        String ext = getExtension(original, file.getContentType());

        String filename = "u" + userId + "_" + System.currentTimeMillis() + "." + ext;

        try {
            Path dirPath = Paths.get(avatarDir).toAbsolutePath().normalize();
            Files.createDirectories(dirPath);

            Path target = dirPath.resolve(filename).normalize();
            // overwrite not needed, but safe
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            // Public URL (served by Spring static mapping, see note below)
            return publicBaseUrl + "/uploads/avatars/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("Upload avatar failed");
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new RuntimeException("Avatar must be <= 2MB");
        }
        String ct = file.getContentType();
        if (!StringUtils.hasText(ct) || !ALLOWED_CONTENT_TYPES.contains(ct)) {
            throw new RuntimeException("Only jpg/png/webp are allowed");
        }
    }

    private String getExtension(String originalFilename, String contentType) {
        if (originalFilename != null && originalFilename.contains(".")) {
            String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
            if (ext.equals("jpg")) return "jpeg";
            if (ext.equals("jpeg") || ext.equals("png") || ext.equals("webp")) return ext;
        }
        // fallback by content type
        if ("image/png".equals(contentType)) return "png";
        if ("image/webp".equals(contentType)) return "webp";
        return "jpeg";
    }
}
