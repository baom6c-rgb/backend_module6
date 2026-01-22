package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.entity.User;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface FileExtractService {
    String extractAndSave(MultipartFile file, User user) throws IOException;
}