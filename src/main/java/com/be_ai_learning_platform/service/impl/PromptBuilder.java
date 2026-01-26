package com.be_ai_learning_platform.service.impl;

import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public String buildPrompt(String materialText, int numberOfQuestions) {
        return """
Bạn là hệ thống tạo câu hỏi trắc nghiệm để HỌC VIÊN tự kiểm tra kiến thức.

NHIỆM VỤ:
Tạo CHÍNH XÁC %d câu hỏi trắc nghiệm dựa DUY NHẤT vào nội dung tài liệu bên dưới.

QUY TẮC BẮT BUỘC:
- Mỗi câu hỏi phải có ĐÚNG 4 đáp án: A, B, C, D
- CHỈ có 1 đáp án đúng
- Câu hỏi và đáp án phải BÁM SÁT tài liệu
- KHÔNG sử dụng kiến thức bên ngoài tài liệu
- KHÔNG thêm lời giải/giải thích/bình luận
- Nếu tài liệu ít thông tin, hãy diễn đạt lại các ý có trong tài liệu để tạo câu hỏi (không bịa ngoài tài liệu)

TÀI LIỆU:
\"\"\"
%s
\"\"\"
""".formatted(numberOfQuestions, materialText);
    }
}
