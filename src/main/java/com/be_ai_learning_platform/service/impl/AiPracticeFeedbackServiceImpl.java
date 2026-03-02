package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.AI.GeminiResponsesClient;
import com.be_ai_learning_platform.dto.response.AttemptReviewItemResponse;
import com.be_ai_learning_platform.entity.enums.QuestionType;
import com.be_ai_learning_platform.service.AiPracticeFeedbackService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AiPracticeFeedbackServiceImpl implements AiPracticeFeedbackService {

    private static final int MAX_AI_FEEDBACK_CHARS = 4200;

    private final GeminiResponsesClient responsesClient;

    public AiPracticeFeedbackServiceImpl(GeminiResponsesClient responsesClient) {
        this.responsesClient = responsesClient;
    }

    @Override
    public String generateFeedback(String userFullName, Integer scorePct, List<AttemptReviewItemResponse> items) {
        int score = scorePct == null ? 0 : scorePct;

        try {
            String prompt = buildGeminiStylePrompt(score, items);
            String raw = responsesClient.generateText(prompt);

            String cleaned = normalizeGeminiOutput(raw);

            if (!looksValidGeminiOutput(cleaned)) {
                return buildSimpleFallback(score);
            }
            return cleaned;

        } catch (Exception e) {
            return buildSimpleFallback(score);
        }
    }

    // =========================================================
    // Gemini-like Prompt (NO markdown **, NO bullets, 2 blocks only)
    // =========================================================
    private String buildGeminiStylePrompt(int scorePct, List<AttemptReviewItemResponse> items) {
        StringBuilder sb = new StringBuilder();

        sb.append("""
Bạn là trợ giảng chuyên về lập trình (Java / Spring / Web Backend). Dựa CHỈ trên dữ liệu bài làm bên dưới, hãy viết nhận xét theo phong cách giống Gemini.

YÊU CẦU ĐẦU RA (BẮT BUỘC):
- Chỉ trả về đúng 2 phần theo đúng thứ tự và đúng tiêu đề sau (không thêm gì khác):

Highlights
<một đoạn văn>

Focus areas
<một đoạn văn>

RÀNG BUỘC FORMAT:
- Không có lời chào.
- Không có bullet, không đánh số.
- Không dùng markdown, không dùng ký tự **, không dùng backtick, không code block.
- Mỗi đoạn 4 đến 7 câu, rõ ràng, đi thẳng vào vấn đề.
- Xưng "bạn".

HƯỚNG DẪN NỘI DUNG:
1) Highlights:
- Tự động gom các câu làm đúng thành 1 đến 2 nhóm kiến thức lớn.
- Nêu rõ bạn làm tốt gì, và liệt kê các keyword/annotation/khái niệm cụ thể xuất hiện trong dữ liệu (nếu đủ dữ liệu thì nêu khoảng 6 keyword).
- Có nhắc đến điểm tổng hoặc tỷ lệ đúng để phản ánh mức độ nắm kiến thức.

2) Focus areas:
- Chỉ chọn 1 chủ đề yếu quan trọng nhất (tối đa 2 nếu thật sự cần).
- Giải thích rõ bạn đang nhầm gì, vì sao sai, và kiến thức đúng là gì (ngắn gọn nhưng đúng bản chất).
- Có thể so sánh ngắn gọn kiểu “A dùng khi..., B dùng khi...”, nhưng không chép lại câu hỏi dài.

TRƯỜNG HỢP ĐẶC BIỆT:
- Nếu đúng 100%: Focus areas ghi đúng câu sau:
Bạn không có điểm yếu nào trong bài kiểm tra này. Hãy tiếp tục phát huy!

DỮ LIỆU BÀI LÀM:
Điểm tổng: """).append(scorePct).append("/100\n");

        if (items != null) {
            int idx = 1;
            for (AttemptReviewItemResponse it : items) {
                if (it == null) continue;

                sb.append("\n---\n");
                sb.append("Câu ").append(idx++).append("\n");
                sb.append("Loại: ").append(it.getQuestionType()).append("\n");
                sb.append("Nội dung: ").append(truncate(safe(it.getContent()), 650)).append("\n");

                boolean correct = computeIsCorrect(it);
                sb.append("Kết quả: ").append(correct ? "Đúng" : "Sai").append("\n");

                Integer sc = it.getScore() == null ? 0 : it.getScore();
                Integer mx = it.getMaxScore() == null ? 0 : it.getMaxScore();
                sb.append("Điểm: ").append(sc).append("/").append(mx).append("\n");

                if (it.getQuestionType() == QuestionType.MCQ) {
                    sb.append("Chọn: ").append(truncate(safe(it.getSelectedAnswer()), 160)).append("\n");
                    sb.append("Đáp án đúng: ").append(truncate(safe(it.getCorrectAnswer()), 160)).append("\n");
                } else {
                    // giữ ngắn để tránh prompt dài quá
                    sb.append("Trả lời: ").append(truncate(safe(it.getYourAnswer()), 260)).append("\n");
                    sb.append("Đáp án mẫu: ").append(truncate(safe(it.getSampleAnswer()), 260)).append("\n");
                }

                if (it.getFeedback() != null && !it.getFeedback().isBlank()) {
                    sb.append("Giải thích/Feedback: ").append(truncate(safe(it.getFeedback()), 420)).append("\n");
                }
            }
        }

        return sb.toString();
    }

    // =========================================================
    // Output normalizer (strip markdown/bullets if model returns)
    // =========================================================
    private String normalizeGeminiOutput(String raw) {
        if (raw == null) return "";
        String text = raw.trim();

        // strip code fence
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "");
            text = text.replaceFirst("\\s*```\\s*$", "");
            text = text.trim();
        }

        // remove markdown markers we don't want
        text = text.replace("**", "");
        text = text.replace("`", "");

        // remove leading greeting lines if any
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            String l = line.trim();
            if (l.isBlank()) continue;

            String low = l.toLowerCase(Locale.ROOT);
            if (low.startsWith("chào ")) continue;
            if (low.startsWith("xin chào ")) continue;

            // remove bullets / numbering if model adds
            if (l.startsWith("•")) l = l.replaceFirst("^•\\s*", "");
            if (l.startsWith("-")) l = l.replaceFirst("^\\-\\s*", "");
            if (l.matches("^\\d+\\)\\s+.+$")) l = l.replaceFirst("^\\d+\\)\\s+", "");
            if (l.matches("^\\d+\\.\\s+.+$")) l = l.replaceFirst("^\\d+\\.\\s+", "");

            lines.add(l);
        }

        String joined = String.join("\n", lines).trim();
        if (joined.length() > MAX_AI_FEEDBACK_CHARS) {
            joined = joined.substring(0, MAX_AI_FEEDBACK_CHARS);
        }
        return joined;
    }

    private boolean looksValidGeminiOutput(String text) {
        if (text == null || text.isBlank()) return false;

        int hi = indexOfIgnoreCase(text, "Highlights");
        int fa = indexOfIgnoreCase(text, "Focus areas");

        // require both headings, correct order
        if (hi < 0 || fa < 0 || fa <= hi) return false;

        // must have some content after each
        String afterHi = text.substring(hi + "Highlights".length()).trim();
        if (afterHi.isBlank()) return false;

        String afterFa = text.substring(fa + "Focus areas".length()).trim();
        if (afterFa.isBlank()) return false;

        // discourage placeholder-like outputs
        if (text.contains("...") || text.contains("…")) return false;

        return true;
    }

    // =========================================================
    // Fallback (still Gemini-like, NO markdown)
    // =========================================================
    private String buildSimpleFallback(int scorePct) {
        return "Highlights\n"
                + "Bạn đã hoàn thành bài kiểm tra và đạt " + scorePct + "/100. Việc hoàn thành bài là tốt, giờ bạn chỉ cần ôn tập có trọng tâm để tăng độ chắc. Hãy xem lại các câu đúng để củng cố nền tảng và duy trì nhịp học đều.\n\n"
                + "Focus areas\n"
                + "Hãy ưu tiên xem lại các câu sai và chốt lại bản chất kiến thức đúng cho từng lỗi. Tập trung vào 1 đến 2 chủ đề bạn sai nhiều nhất trước, rồi làm thêm vài câu cùng chủ đề để tránh lặp lại lỗi tương tự.";
    }

    // =========================================================
    // Correctness helpers
    // =========================================================
    private boolean computeIsCorrect(AttemptReviewItemResponse it) {
        if (it == null) return false;

        if (it.getIsCorrect() != null) {
            return Boolean.TRUE.equals(it.getIsCorrect());
        }

        if (it.getQuestionType() == QuestionType.MCQ) {
            String sel = safe(it.getSelectedAnswer());
            String right = safe(it.getCorrectAnswer());
            return !sel.isBlank() && sel.equalsIgnoreCase(right);
        }

        // Essay: treat "correct" only when full score (best-effort)
        Integer sc = it.getScore() == null ? 0 : it.getScore();
        Integer mx = it.getMaxScore() == null ? 0 : it.getMaxScore();
        return mx > 0 && sc >= mx;
    }

    private int indexOfIgnoreCase(String text, String needle) {
        if (text == null || needle == null) return -1;
        return text.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() <= max) return t;
        return t.substring(0, max);
    }
}