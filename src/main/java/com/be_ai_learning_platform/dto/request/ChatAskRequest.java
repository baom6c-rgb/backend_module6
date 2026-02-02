package com.be_ai_learning_platform.dto.request;

import lombok.Data;

@Data
public class ChatAskRequest {
    /**
     * US17: chỉ cho nhập từ khóa (keywords), không được paste nguyên câu hỏi quiz.
     * Ví dụ hợp lệ: "jwt refresh token", "spring security filter"...
     */
    private String keywords;
}
