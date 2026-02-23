package com.be_ai_learning_platform.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class GeneratePracticeSessionResponse {
    /**
     * READY: đã tạo xong sessionToken, FE có thể bấm "Bắt đầu".
     * NEED_TOPIC: học liệu có nhiều bài/chủ đề, cần chọn 1 topic trước khi tạo đề.
     */
    private String status;

    private String sessionToken;
    private Long materialId;
    private Integer numberOfQuestions;
    private Integer durationMinutes;

    // ===== topic selection flow (optional) =====
    private String selectionToken;
    private List<TopicOptionResponse> topics;
}
