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
    public Long extractAndSave(MultipartFile file, User user) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File không hợp lệ");
        }

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null) {
            throw new IllegalArgumentException("Tên file không hợp lệ");
        }

        String lowerName = originalFileName.toLowerCase();
        FileType fileType;
        String extractedText;

        // 1️⃣ Extract text theo định dạng
        if (lowerName.endsWith(".pdf")) {
            fileType = FileType.PDF;
            try (PDDocument document = Loader.loadPDF(file.getBytes())) {
                PDFTextStripper stripper = new PDFTextStripper();
                extractedText = stripper.getText(document);
            }

        } else if (lowerName.endsWith(".docx")) {
            fileType = FileType.DOCX;
            try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
                extractedText = new XWPFWordExtractor(doc).getText();
            }

        } else if (lowerName.endsWith(".txt")) {
            fileType = FileType.TXT;
            extractedText = new String(file.getBytes(), StandardCharsets.UTF_8);

        } else {
            throw new IllegalArgumentException(
                    "Định dạng file không được hỗ trợ. Chỉ chấp nhận PDF, DOCX, TXT."
            );
        }

        // 2️⃣ Lưu DB
        LearningMaterial material = new LearningMaterial();
        material.setUser(user);
        material.setFileName(originalFileName);
        material.setFileType(fileType);
        material.setFileSize(file.getSize());
        material.setExtractedText(extractedText);
        material.setStatus(MaterialStatus.EXTRACTED);

        materialRepository.save(material);

        return material.getId();
    }
}
