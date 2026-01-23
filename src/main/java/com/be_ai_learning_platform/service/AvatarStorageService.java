package com.be_ai_learning_platform.service;

import org.springframework.web.multipart.MultipartFile;

public interface AvatarStorageService {
    /**
     * Save avatar file and return public URL to access it.
     */
    String storeAvatar(MultipartFile file, Long userId);
}
