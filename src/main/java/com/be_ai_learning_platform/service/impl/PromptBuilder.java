package com.be_ai_learning_platform.service.impl;

import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    // Ratio ESSAY ~ 30% (min 1)
    private static final double ESSAY_RATIO = 0.3;

    public String buildPrompt(String materialText, int numberOfQuestions) {
        int essayCount = Math.max(1, (int) Math.round(numberOfQuestions * ESSAY_RATIO));
        int mcqCount = Math.max(0, numberOfQuestions - essayCount);

        return """
Bạn là hệ thống tạo đề luyện tập cho HỌC VIÊN dựa DUY NHẤT vào tài liệu bên dưới.

NHIỆM VỤ:
- Tạo CHÍNH XÁC %d câu hỏi, gồm:
  - %d câu TRẮC NGHIỆM (MCQ)
  - %d câu TỰ LUẬN NGẮN (ESSAY)
- Trộn NGẪU NHIÊN thứ tự câu hỏi (MCQ + ESSAY xen kẽ).

QUY TẮC BẮT BUỘC:
- Bám sát tài liệu, KHÔNG dùng kiến thức ngoài tài liệu.
- Không thêm nội dung lan man ngoài JSON.
- Output phải là JSON HỢP LỆ theo schema bên dưới.
- Giới hạn độ dài để tránh output quá dài:
  - MCQ: question <= 160 ký tự, mỗi option <= 80 ký tự, analysis <= 200 ký tự
  - ESSAY: question <= 200 ký tự, sampleAnswer <= 500 ký tự, keywords 3-6 từ/cụm từ

SCHEMA JSON (BẮT BUỘC):
{
  "questions": [
    {
      "questionType": "MCQ",
      "question": "...",
      "options": { "A": "...", "B": "...", "C": "...", "D": "..." },
      "correctAnswer": "A|B|C|D",
      "analysis": "Giải thích ngắn (1-2 câu), bám sát tài liệu"
    },
    {
      "questionType": "ESSAY",
      "question": "...",
      "sampleAnswer": "Đáp án mẫu ngắn gọn (2-4 câu)",
      "keywords": ["keyword1","keyword2","keyword3"],
      "maxScore": 10
    }
  ]
}

TÀI LIỆU:
\"\"\"
%s
\"\"\"
""".formatted(numberOfQuestions, mcqCount, essayCount, materialText);
    }

    public String buildRetestPrompt(String materialText, int numberOfQuestions, String focusText) {
        int essayCount = Math.max(1, (int) Math.round(numberOfQuestions * ESSAY_RATIO));
        int mcqCount = Math.max(0, numberOfQuestions - essayCount);

        String focus = (focusText == null) ? "" : focusText.trim();
        if (focus.length() > 2500) focus = focus.substring(0, 2500);

        return """
Bạn là hệ thống tạo đề THI LẠI (RETEST) cho HỌC VIÊN, dựa DUY NHẤT vào tài liệu bên dưới.

MỤC TIÊU RETEST:
- Chỉ tập trung vào các chủ đề/ý mà học viên đã làm sai hoặc yếu (WEAK AREAS).
- Ưu tiên hỏi sâu, xoáy vào lỗi, không hỏi lan man ngoài WEAK AREAS.

NHIỆM VỤ:
- Tạo CHÍNH XÁC %d câu hỏi, gồm:
  - %d câu TRẮC NGHIỆM (MCQ)
  - %d câu TỰ LUẬN NGẮN (ESSAY)
- Trộn NGẪU NHIÊN thứ tự câu hỏi (MCQ + ESSAY xen kẽ).

WEAK AREAS (BẮT BUỘC bám sát):
\"\"\"
%s
\"\"\"

QUY TẮC BẮT BUỘC:
- Bám sát tài liệu, KHÔNG dùng kiến thức ngoài tài liệu.
- Không thêm nội dung lan man ngoài JSON.
- Output phải là JSON HỢP LỆ theo schema bên dưới.
- Giới hạn độ dài để tránh output quá dài:
  - MCQ: question <= 160 ký tự, mỗi option <= 80 ký tự, analysis <= 200 ký tự
  - ESSAY: question <= 200 ký tự, sampleAnswer <= 500 ký tự, keywords 3-6 từ/cụm từ

SCHEMA JSON (BẮT BUỘC):
{
  "questions": [
    {
      "questionType": "MCQ",
      "question": "...",
      "options": { "A": "...", "B": "...", "C": "...", "D": "..." },
      "correctAnswer": "A|B|C|D",
      "analysis": "Giải thích ngắn (1-2 câu), bám sát tài liệu"
    },
    {
      "questionType": "ESSAY",
      "question": "...",
      "sampleAnswer": "Đáp án mẫu ngắn gọn (2-4 câu)",
      "keywords": ["keyword1","keyword2","keyword3"],
      "maxScore": 10
    }
  ]
}

TÀI LIỆU:
\"\"\"
%s
\"\"\"
""".formatted(numberOfQuestions, mcqCount, essayCount, focus, materialText);
    }
}
