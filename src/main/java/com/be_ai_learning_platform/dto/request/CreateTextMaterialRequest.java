package com.be_ai_learning_platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateTextMaterialRequest {

    // optional: FE đặt tiêu đề hiển thị
    private String title;

    @NotBlank(message = "Nội dung không được để trống")
    private String rawText;
}
