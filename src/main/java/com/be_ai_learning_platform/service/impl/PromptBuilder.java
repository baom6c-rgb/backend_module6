package com.be_ai_learning_platform.service.impl;

import org.springframework.stereotype.Component;

/**
 * Prompt builder for generating practice questions.
 *
 * Design goals:
 * - Generate questions that test understanding and application (not memorization).
 * - Create plausible distractors based on common misconceptions.
 * - Provide analysis that explains why the correct option is correct AND why the others are wrong.
 */
@Component
public class PromptBuilder {

    private static final double ESSAY_RATIO = 0.3;

    private static final int MAX_MATERIAL_CHARS = 6000;
    private static final int MAX_FOCUS_CHARS = 2500;

    private static final int MCQ_QUESTION_MAX = 320;
    private static final int MCQ_OPTION_MAX = 120;
    private static final int MCQ_ANALYSIS_MAX = 380;
    private static final int ESSAY_QUESTION_MAX = 260;
    private static final int ESSAY_SAMPLE_MAX = 480;

    /**
     * ✅ Backward-compatible: giữ nguyên signature cũ.
     * Nếu chưa có focusText thì generate theo toàn bộ material như trước.
     */
    public String buildPrompt(String materialText, int mcqCount, int essayCount) {
        return buildPrompt(materialText, mcqCount, essayCount, null);
    }

    /**
     * ✅ NEW: Normal practice có thêm focusText.
     * - focusText dùng khi user chọn 1 hoặc nhiều nội dung muốn luyện tập.
     * - Không thay đổi các rule khác, chỉ thêm "FOCUS AREAS" và yêu cầu ưu tiên bám sát focus.
     *
     * totalQuestions = mcqCount + essayCount (validate ở service rồi).
     */
    public String buildPrompt(String materialText, int mcqCount, int essayCount, String focusText) {
        String material = normalizeAndTrim(materialText, MAX_MATERIAL_CHARS);

        String focus = normalizeAndTrim(focusText, MAX_FOCUS_CHARS);
        boolean hasFocus = !focus.isBlank();

        int mcq = Math.max(0, mcqCount);
        int essay = Math.max(0, essayCount);
        int totalQuestions = mcq + essay;

        String focusBlock = hasFocus
                ? """
                
FOCUS AREAS (ƯU TIÊN BẮT BUỘC):
- Người học đã chọn các phần muốn luyện tập dưới đây.
- Hãy ƯU TIÊN tạo câu hỏi xoay quanh FOCUS AREAS.
- Tránh hỏi lan man ngoài FOCUS AREAS, trừ khi cần 1-2 câu để nối ngữ cảnh.
- Tuy nhiên: vẫn chỉ được dùng thông tin có trong TÀI LIỆU (không dùng kiến thức ngoài).
- Nếu FOCUS AREAS quá ngắn/thiếu dữ kiện, hãy tạo câu hỏi dựa trên phần liên quan nhất trong TÀI LIỆU,
  nhưng vẫn cố gắng bám sát ý định của FOCUS AREAS.

\"\"\"
%s
\"\"\"
""".formatted(focus)
                : "";

        return """
Bạn là hệ thống tạo đề luyện tập cho HỌC VIÊN dựa DUY NHẤT vào tài liệu bên dưới.

MỤC TIÊU (RẤT QUAN TRỌNG):
- Không chỉ kiểm tra trí nhớ. Ưu tiên câu hỏi giúp học viên HIỂU và VẬN DỤNG.
- Câu hỏi nên theo kiểu MỞ RỘNG nhưng vẫn BÁM SÁT nội dung tài liệu (không dùng kiến thức ngoài).
- Có thể chèn code/config ngắn trong câu hỏi (nếu phù hợp) để học viên tự chạy/thử và kiểm tra hiểu bài.
%s
NHIỆM VỤ:
- Tạo CHÍNH XÁC %d câu hỏi, gồm:
  - %d câu TRẮC NGHIỆM (MCQ)
  - %d câu TỰ LUẬN NGẮN (ESSAY)
- Trộn NGẪU NHIÊN thứ tự câu hỏi (MCQ + ESSAY xen kẽ).

BẮT BUỘC VỀ DẠNG CÂU HỎI (CHỐNG TOÀN KHÁI NIỆM):
- Ít nhất 40%% số câu MCQ phải thuộc nhóm “CODE/CONFIG/LOG/REQUEST-RESPONSE”:
  1) Đọc đoạn code/config (ngắn) và hỏi: kết quả gì? bug gì? thiếu gì?
  2) Điền chỗ trống ____ trong code/config: chọn A/B/C/D.
  3) Chọn cấu hình đúng (VD: Spring MVC mapping, CORS, Security/JWT, Validation, JSON, v.v.)
  4) Nhìn log/response và chọn nguyên nhân đúng nhất.
- Không được quá 20%% tổng số câu hỏi là dạng “định nghĩa/khái niệm là gì” thuần túy.
  Nếu hỏi khái niệm, BẮT BUỘC phải gắn tình huống hoặc đoạn code/config minh họa.

NGUYÊN TẮC THIẾT KẾ CÂU HỎI:
1) BÁM SÁT TÀI LIỆU: chỉ dùng thông tin có trong tài liệu.
2) Ưu tiên dạng câu hỏi vận dụng:
   - Tình huống thực tế, "điều gì xảy ra nếu...", "cách xử lý đúng trong trường hợp..."
   - So sánh lựa chọn, trade-off, best practice nêu trong tài liệu
   - Đọc code/config ngắn và suy luận kết quả/lỗi/rủi ro
3) Tránh câu hỏi thuần ghi nhớ (định nghĩa đơn thuần) trừ khi thật sự cần.

PHƯƠNG ÁN NHIỄU (DISTRACTORS) CHO MCQ (BẮT BUỘC):
- Tất cả phương án sai phải "trông có vẻ hợp lý".
- Phương án sai PHẢI dựa trên:
  - lỗi sai phổ biến của người học
  - khái niệm dễ nhầm lẫn có trong tài liệu
  - hiểu sai bối cảnh/điều kiện áp dụng
- Tuyệt đối không tạo đáp án sai kiểu vô lý, hài hước, hoặc sai hiển nhiên.

PHẦN GIẢI THÍCH (analysis) CHO MCQ (BẮT BUỘC):
- Phải nêu rõ:
  - Vì sao đáp án đúng là đúng (gắn với chi tiết trong tài liệu)
  - Vì sao từng phương án còn lại sai/chưa chính xác (A/B/C/D đều phải được nhắc đến)
- Viết theo cấu trúc ngắn gọn, rõ ràng, dạng gạch đầu dòng càng tốt.

BẮT BUỘC RIÊNG CHO ESSAY (KHÔNG ĐƯỢC THIẾU):
- Mỗi câu ESSAY PHẢI có trường "keywords".
- "keywords" là mảng 3–6 từ/cụm từ, KHÔNG được rỗng, KHÔNG chứa chuỗi trống.
- Keywords phải bám sát nội dung câu hỏi và tài liệu, ưu tiên thuật ngữ kỹ thuật xuất hiện trong tài liệu.


GIỚI HẠN ĐỘ DÀI (để tránh output quá dài):
- MCQ: question <= %d ký tự; mỗi option <= %d ký tự; analysis <= %d ký tự
- ESSAY: question <= %d ký tự; sampleAnswer <= %d ký tự; keywords 3-6 từ/cụm từ

YÊU CẦU OUTPUT:
- Output phải là JSON HỢP LỆ theo schema bên dưới.
- Không thêm bất kỳ nội dung/markdown nào ngoài JSON.
- Nếu có ESSAY mà thiếu "keywords" hoặc keywords rỗng => coi như trả lời SAI, hãy tự sửa trước khi output.

SCHEMA JSON (BẮT BUỘC):
{
  "questions": [
    {
      "questionType": "MCQ",
      "question": "...",
      "options": { "A": "...", "B": "...", "C": "...", "D": "..." },
      "correctAnswer": "A|B|C|D",
      "analysis": "Giải thích: vì sao đúng + vì sao từng phương án còn lại sai (nhắc đủ A/B/C/D)"
    },
    {
      "questionType": "ESSAY",
      "question": "...",
      "sampleAnswer": "Đáp án mẫu ngắn gọn (2-6 câu), tập trung vào lập luận/áp dụng",
      "keywords": ["keyword1","keyword2","keyword3"],
      "maxScore": 10
    }
  ]
}

TÀI LIỆU:
\"\"\"
%s
\"\"\"
""".formatted(
                focusBlock,
                totalQuestions,
                mcq,
                essay,
                MCQ_QUESTION_MAX,
                MCQ_OPTION_MAX,
                MCQ_ANALYSIS_MAX,
                ESSAY_QUESTION_MAX,
                ESSAY_SAMPLE_MAX,
                material
        );
    }

    /**
     * ✅ Retest: count cũng lấy từ settings, focusText vẫn giữ nguyên prompt.
     * Signature khớp với service của mày: buildRetestPrompt(trimmed, mcq, essay, focusText)
     */
    public String buildRetestPrompt(String materialText, int mcqCount, int essayCount, String focusText) {
        String material = normalizeAndTrim(materialText, MAX_MATERIAL_CHARS);
        String focus = normalizeAndTrim(focusText, MAX_FOCUS_CHARS);

        int mcq = Math.max(0, mcqCount);
        int essay = Math.max(0, essayCount);
        int totalQuestions = mcq + essay;

        return """
Bạn là hệ thống tạo đề THI LẠI (RETEST) cho HỌC VIÊN, dựa DUY NHẤT vào tài liệu bên dưới.

MỤC TIÊU RETEST:
- Chỉ tập trung vào các chủ đề/ý mà học viên đã làm sai hoặc yếu (WEAK AREAS).
- Ưu tiên hỏi sâu, xoáy vào lỗi, tránh hỏi lan man ngoài WEAK AREAS.

YÊU CẦU CHẤT LƯỢNG:
- Không chỉ kiểm tra trí nhớ. Ưu tiên vận dụng, suy luận, tình huống, đọc code/config ngắn.
- MCQ phải có phương án nhiễu hợp lý dựa trên các lỗi sai phổ biến/nhầm lẫn trong tài liệu.
- Analysis phải giải thích vì sao đúng + vì sao từng phương án sai (nhắc đủ A/B/C/D).

NHIỆM VỤ:
- Tạo CHÍNH XÁC %d câu hỏi, gồm:
  - %d câu TRẮC NGHIỆM (MCQ)
  - %d câu TỰ LUẬN NGẮN (ESSAY)
- Trộn NGẪU NHIÊN thứ tự câu hỏi (MCQ + ESSAY xen kẽ).

BẮT BUỘC VỀ DẠNG CÂU HỎI (CHỐNG TOÀN KHÁI NIỆM):
- Ít nhất 40%% số câu MCQ phải thuộc nhóm “CODE/CONFIG/LOG/REQUEST-RESPONSE”:
  1) Đọc đoạn code/config (ngắn) và hỏi: kết quả gì? bug gì? thiếu gì?
  2) Điền chỗ trống ____ trong code/config: chọn A/B/C/D.
  3) Chọn cấu hình đúng (VD: Spring MVC mapping, CORS, Security/JWT, Validation, JSON, v.v.)
  4) Nhìn log/response và chọn nguyên nhân đúng nhất.
- Không được quá 20%% tổng số câu hỏi là dạng “định nghĩa/khái niệm là gì” thuần túy.
  Nếu hỏi khái niệm, BẮT BUỘC phải gắn tình huống hoặc đoạn code/config minh họa.

WEAK AREAS (BẮT BUỘC bám sát):
\"\"\"
%s
\"\"\"

BẮT BUỘC RIÊNG CHO ESSAY (KHÔNG ĐƯỢC THIẾU):
- Mỗi câu ESSAY PHẢI có trường "keywords".
- "keywords" là mảng 3–6 từ/cụm từ, KHÔNG được rỗng, KHÔNG chứa chuỗi trống.
- Keywords phải bám sát nội dung câu hỏi và tài liệu, ưu tiên thuật ngữ kỹ thuật xuất hiện trong tài liệu.


GIỚI HẠN ĐỘ DÀI (để tránh output quá dài):
- MCQ: question <= %d ký tự; mỗi option <= %d ký tự; analysis <= %d ký tự
- ESSAY: question <= %d ký tự; sampleAnswer <= %d ký tự; keywords 3-6 từ/cụm từ

YÊU CẦU OUTPUT:
- Output phải là JSON HỢP LỆ theo schema bên dưới.
- Không thêm bất kỳ nội dung/markdown nào ngoài JSON.
- Nếu có ESSAY mà thiếu "keywords" hoặc keywords rỗng => coi như trả lời SAI, hãy tự sửa trước khi output.


SCHEMA JSON (BẮT BUỘC):
{
  "questions": [
    {
      "questionType": "MCQ",
      "question": "...",
      "options": { "A": "...", "B": "...", "C": "...", "D": "..." },
      "correctAnswer": "A|B|C|D",
      "analysis": "Giải thích: vì sao đúng + vì sao từng phương án còn lại sai (nhắc đủ A/B/C/D)"
    },
    {
      "questionType": "ESSAY",
      "question": "...",
      "sampleAnswer": "Đáp án mẫu ngắn gọn (2-6 câu), tập trung vào lập luận/áp dụng",
      "keywords": ["keyword1","keyword2","keyword3"],
      "maxScore": 10
    }
  ]
}

GHI CHÚ BẮT BUỘC:
- Với ESSAY: "keywords" là REQUIRED, phải có 3-6 phần tử, không rỗng.

TÀI LIỆU:
\"\"\"
%s
\"\"\"
""".formatted(
                totalQuestions,
                mcq,
                essay,
                focus,
                MCQ_QUESTION_MAX,
                MCQ_OPTION_MAX,
                MCQ_ANALYSIS_MAX,
                ESSAY_QUESTION_MAX,
                ESSAY_SAMPLE_MAX,
                material
        );
    }

    private static String normalizeAndTrim(String s, int maxChars) {
        if (s == null) return "";
        String t = s.trim();
        if (t.isEmpty()) return "";
        if (t.length() <= maxChars) return t;
        return t.substring(0, maxChars);
    }
}