package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.entity.LearningMaterial;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.FileType;
import com.be_ai_learning_platform.entity.enums.MaterialStatus;
import com.be_ai_learning_platform.repository.LearningMaterialRepository;
import com.be_ai_learning_platform.service.FileExtractService;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class FileExtractServiceImpl implements FileExtractService {

    private final LearningMaterialRepository materialRepository;

    @Override
    public String extractAndSave(MultipartFile file, User user) throws IOException {
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null) throw new RuntimeException("Tên file không hợp lệ");

        String fileName = originalFileName.toLowerCase();
        String content = "";
        FileType type;

        // 1. Kiểm tra định dạng file (Validation)
        if (fileName.endsWith(".pdf")) {
            type = FileType.PDF;
            try (PDDocument document = Loader.loadPDF(file.getBytes())) {
                content = new PDFTextStripper().getText(document);
            }
        } else if (fileName.endsWith(".docx")) {
            type = FileType.DOCX;
            try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
                content = new XWPFWordExtractor(doc).getText();
            }
        } else if (fileName.endsWith(".txt")) {
            type = FileType.TXT;
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } else {
            // 2. Báo lỗi nếu không đúng định dạng (Yêu cầu của bạn)
            throw new RuntimeException("Định dạng file không được hỗ trợ. Chỉ chấp nhận PDF, DOCX, TXT.");
        }

        // 3. Lưu thông tin
        LearningMaterial material = new LearningMaterial();
        material.setUser(user);
        material.setFileName(originalFileName);
        material.setFileType(type);
        material.setFileSize(file.getSize());
        material.setExtractedText(content);
        material.setStatus(MaterialStatus.EXTRACTED);

        materialRepository.save(material);
        return content;
    }
}