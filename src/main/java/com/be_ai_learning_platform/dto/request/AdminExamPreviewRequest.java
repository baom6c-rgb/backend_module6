package com.be_ai_learning_platform.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class AdminExamPreviewRequest {

    // chọn 1 trong 2: materialId hoặc inputText
    private Long materialId;
    private String inputText;

    /**
     * ✅ ADMIN chọn cơ cấu câu hỏi
     * - mcqCount + essayCount = tổng số câu
     */
    @Min(0)
    @Max(30)
    private Integer mcqCount;

    @Min(0)
    @Max(30)
    private Integer essayCount;

    /**
     * (Optional) giữ lại để backward-compatible nếu FE cũ vẫn gửi.
     * Nhưng với admin create exam chuẩn mới => dùng mcqCount/essayCount.
     */
    @Min(1)
    @Max(30)
    private Integer numberOfQuestions;

    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }

    public String getInputText() { return inputText; }
    public void setInputText(String inputText) { this.inputText = inputText; }

    public Integer getMcqCount() { return mcqCount; }
    public void setMcqCount(Integer mcqCount) { this.mcqCount = mcqCount; }

    public Integer getEssayCount() { return essayCount; }
    public void setEssayCount(Integer essayCount) { this.essayCount = essayCount; }

    public Integer getNumberOfQuestions() { return numberOfQuestions; }
    public void setNumberOfQuestions(Integer numberOfQuestions) { this.numberOfQuestions = numberOfQuestions; }
}