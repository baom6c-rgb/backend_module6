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
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FileExtractServiceImpl implements FileExtractService {

    private final LearningMaterialRepository materialRepository;

    // ✅ giới hạn để tránh DB nặng + AI nặng
    private static final int MAX_EXTRACTED_CHARS = 200_000;

    @Override
    public Long extractAndSave(MultipartFile file, User user) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File không hợp lệ");
        }

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null) {
            throw new IllegalArgumentException("Tên file không hợp lệ");
        }

        String lowerName = originalFileName.toLowerCase(Locale.ROOT);
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
            try (XWPFDocument doc = new XWPFDocument(file.getInputStream());
                 XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
                extractedText = extractor.getText();
            }

        } else if (lowerName.endsWith(".txt")) {
            fileType = FileType.TXT;
            extractedText = new String(file.getBytes(), StandardCharsets.UTF_8);

        } else if (lowerName.endsWith(".xlsx")) {
            fileType = FileType.XLSX;
            extractedText = extractXlsxToText(file.getInputStream());

        } else {
            throw new IllegalArgumentException(
                    "Định dạng file không được hỗ trợ. Chỉ chấp nhận PDF, DOCX, TXT, XLSX."
            );
        }

        extractedText = safeTrim(extractedText, MAX_EXTRACTED_CHARS);

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

    private String extractXlsxToText(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder(8192);

        try (Workbook wb = new XSSFWorkbook(in)) {
            DataFormatter formatter = new DataFormatter(true);
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                if (sheet == null) continue;

                sb.append("=== Sheet: ").append(sheet.getSheetName()).append(" ===\n");

                for (Row row : sheet) {
                    if (row == null) continue;

                    short lastCell = row.getLastCellNum();
                    if (lastCell <= 0) {
                        sb.append("\n");
                        continue;
                    }

                    for (int c = 0; c < lastCell; c++) {
                        Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);

                        String value = "";
                        if (cell != null) {
                            try {
                                value = formatter.formatCellValue(cell, evaluator);
                            } catch (Exception ignore) {
                                value = "";
                            }
                            value = value == null ? "" : value.replaceAll("\\s+", " ").trim();
                        }

                        sb.append(value);
                        if (c < lastCell - 1) sb.append("\t");
                    }

                    sb.append("\n");

                    // ✅ cắt sớm nếu quá dài
                    if (sb.length() > MAX_EXTRACTED_CHARS + 2000) break;
                }

                sb.append("\n");
                if (sb.length() > MAX_EXTRACTED_CHARS + 2000) break;
            }
        }

        return sb.toString().trim();
    }

    private String safeTrim(String s, int maxChars) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars);
    }
}
