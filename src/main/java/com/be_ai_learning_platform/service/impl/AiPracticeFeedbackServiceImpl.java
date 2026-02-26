package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.AI.GeminiResponsesClient;
import com.be_ai_learning_platform.dto.response.AttemptReviewItemResponse;
import com.be_ai_learning_platform.entity.enums.QuestionType;
import com.be_ai_learning_platform.service.AiPracticeFeedbackService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class AiPracticeFeedbackServiceImpl implements AiPracticeFeedbackService {

    private static final int MAX_AI_FEEDBACK_CHARS = 3500;

    // Required bullet format: * **[Nhóm]:** ...
    private static final Pattern GROUP_BULLET =
            Pattern.compile("^\\*\\s+\\*\\*.+\\*\\*:\\s+.+$");

    private final GeminiResponsesClient responsesClient;

    public AiPracticeFeedbackServiceImpl(GeminiResponsesClient responsesClient) {
        this.responsesClient = responsesClient;
    }

    @Override
    public String generateFeedback(String userFullName, Integer scorePct, List<AttemptReviewItemResponse> items) {
        String name = (userFullName == null || userFullName.isBlank()) ? "bạn" : userFullName.trim();
        int score = scorePct == null ? 0 : scorePct;
        List<AttemptReviewItemResponse> safeItems = items == null ? List.of() : items;

        String raw = "";
        try {
            String prompt = buildPrompt(name, score, safeItems);
            raw = safeTrim(responsesClient.generateText(prompt), MAX_AI_FEEDBACK_CHARS);
        } catch (Exception ignored) {
            // fallback below
        }

        String formatted = formatAiFeedback(name, raw);

        // Guard: đúng cấu trúc + đúng bullet format + cấm placeholder
        if (!isValidFeedback(formatted, safeItems)) {
            formatted = buildRuleBasedFeedback(name, score, safeItems);
        }
        return formatted;
    }

    // =========================================================
    // Prompt builder (Gemini-like, grounded to input)
    // =========================================================
    private String buildPrompt(String name, int scorePct, List<AttemptReviewItemResponse> items) {
        StringBuilder sb = new StringBuilder();

        sb.append("""
Bạn là trợ giảng đại học. Hãy nhận xét bài làm của học viên bằng tiếng Việt, dựa CHỈ trên dữ liệu câu hỏi bên dưới.

YÊU CẦU BẮT BUỘC:
- BẮT ĐẦU bằng đúng 1 câu chào: "Chào %s,"
- Chỉ trả về đúng 2 phần: "Điểm mạnh" và "Điểm yếu". Không thêm phần khác.

FORMAT (BẮT BUỘC):
Điểm mạnh
* **[Tên nhóm kiến thức]:** [2-3 câu]

Điểm yếu
* **[Tên nhóm kiến thức]:** [2-3 câu]

QUY TẮC FORMAT:
- Header phải đúng y như sau (không thêm dấu ":"):
  "Điểm mạnh"
  "Điểm yếu"
- Mỗi dòng nhóm phải bắt đầu bằng đúng: * **...**: ...
- Không dùng ký hiệu •, không dùng -, không đánh số.
- KHÔNG dùng "...", "…", "(Chưa có)", "(Chưa xác định)".
- KHÔNG dùng backtick ` và KHÔNG dùng code block.

QUY TẮC "BÁM SÁT DỮ LIỆU" (GIỐNG GEMINI):
1) Tự nhóm câu hỏi theo nhóm kiến thức dựa trên NỘI DUNG câu hỏi/đáp án/giải thích.
2) Mỗi nhóm phải nêu rõ:
   - (a) học viên đúng/sai gì (tóm tắt theo dữ liệu)
   - (b) liệt kê ít nhất 2 khái niệm/keyword CỤ THỂ xuất hiện trong dữ liệu (từ câu hỏi hoặc giải thích)
3) Điểm mạnh:
   - Chỉ đưa nhóm mà học viên làm tốt (đúng đa số hoặc đúng các câu trọng tâm).
   - Khen dựa trên điểm và khái niệm đúng (không khen chung chung).
4) Điểm yếu:
   - Chỉ đưa nhóm mà học viên làm sai/cần cải thiện.
   - Phải giải thích ngắn gọn nhưng đúng bản chất: vì sao sai + kiến thức đúng là gì.
5) Nếu học viên làm đúng 100%%:
   - Trong "Điểm yếu" ghi đúng 1 dòng:
     "Không có. Chúc mừng bạn đã nắm vững toàn bộ kiến thức!"

DỮ LIỆU BÀI LÀM:
Điểm tổng: %d/100

Danh sách câu hỏi (mỗi câu có: nội dung, đúng/sai, điểm, đáp án, giải thích/feedback):
""".formatted(name, scorePct));

        int idx = 1;
        for (AttemptReviewItemResponse it : items) {
            if (it == null) continue;
            QuestionType type = it.getQuestionType();

            sb.append("\n---\n");
            sb.append("Câu ").append(idx++).append(" (").append(type).append("): ")
                    .append(safeTrim(it.getContent(), 900)).append("\n");

            Integer sc = it.getScore() == null ? 0 : it.getScore();
            Integer mx = it.getMaxScore() == null ? 0 : it.getMaxScore();
            boolean correct = computeIsCorrect(it);

            sb.append("Kết quả: ").append(correct ? "Đúng" : "Sai").append("\n");
            sb.append("Điểm: ").append(sc).append("/").append(mx).append("\n");

            if (type == QuestionType.MCQ) {
                sb.append("Chọn: ").append(safeStr(it.getSelectedAnswer())).append("\n");
                sb.append("Đúng: ").append(safeStr(it.getCorrectAnswer())).append("\n");
            } else {
                sb.append("Trả lời: ").append(safeTrim(it.getYourAnswer(), 900)).append("\n");
                sb.append("Đáp án mẫu: ").append(safeTrim(it.getSampleAnswer(), 900)).append("\n");
            }

            if (it.getFeedback() != null && !it.getFeedback().isBlank()) {
                sb.append("Giải thích/Feedback: ").append(safeTrim(it.getFeedback(), 900)).append("\n");
            }
        }

        return sb.toString();
    }

    // =========================================================
    // Format + Guard
    // =========================================================
    private String formatAiFeedback(String userFullName, String raw) {
        String name = (userFullName == null || userFullName.isBlank()) ? "bạn" : userFullName.trim();
        String text = raw == null ? "" : raw.trim();

        // strip code fence
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "");
            text = text.replaceFirst("\\s*```\\s*$", "");
            text = text.trim();
        }

        // remove backticks only (KEEP **bold** because spec needs it)
        text = text.replace("`", "").trim();

        List<String> outLines = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            String l = line.trim();
            if (l.isBlank()) continue;

            // normalize headers (allow AI add ":" -> remove)
            if (equalsIgnoreCaseLine(l, "Điểm mạnh:")) l = "Điểm mạnh";
            if (equalsIgnoreCaseLine(l, "Điểm yếu:")) l = "Điểm yếu";

            // normalize bullets to "* "
            if (l.startsWith("•")) l = l.replaceFirst("^•\\s*", "* ");
            if (l.startsWith("-")) l = l.replaceFirst("^\\-\\s*", "* ");
            if (l.startsWith("–")) l = l.replaceFirst("^–\\s*", "* ");
            if (l.startsWith("—")) l = l.replaceFirst("^—\\s*", "* ");
            // numbered list -> bullet
            if (l.matches("^\\d+\\)\\s+.+$")) l = l.replaceFirst("^\\d+\\)\\s+", "* ");
            if (l.matches("^\\d+\\.\\s+.+$")) l = l.replaceFirst("^\\d+\\.\\s+", "* ");

            // ✅ If AI returns "**Nhóm:** ..." without leading bullet, normalize to "* **Nhóm:** ..."
            if (l.matches("^\\*\\*.+\\*\\*:\\s+.+$")) {
                l = "* " + l;
            }

            outLines.add(l);
        }

        if (outLines.isEmpty()) {
            return buildRuleBasedFeedback(name, 0, List.of());
        }

        // ensure greeting
        String first = outLines.get(0);
        boolean hasGreeting = first.toLowerCase(Locale.ROOT).startsWith("chào ");
        String joined = String.join("\n", outLines).trim();
        if (!hasGreeting) {
            joined = ("Chào " + name + ",\n" + joined).trim();
        }

        if (joined.length() > MAX_AI_FEEDBACK_CHARS) {
            joined = joined.substring(0, MAX_AI_FEEDBACK_CHARS);
        }
        return joined;
    }

    private boolean isValidFeedback(String text, List<AttemptReviewItemResponse> items) {
        if (text == null || text.isBlank()) return false;
        if (containsPlaceholder(text)) return false;

        boolean hasStrengthHeader = hasHeader(text, "Điểm mạnh");
        boolean hasWeakHeader = hasHeader(text, "Điểm yếu");
        if (!hasStrengthHeader || !hasWeakHeader) return false;

        String strengthBlock = extractBlock(text, "Điểm mạnh", "Điểm yếu");
        String weakBlock = extractBlock(text, "Điểm yếu", null);

        // ✅ if many questions, require at least 2 groups to avoid generic output
        int minBullets = (items != null && items.size() >= 8) ? 2 : 1;

        boolean strengthOk = countGroupedBullets(strengthBlock) >= minBullets;

        // If 100% correct, allow special line
        boolean allCorrect = isAllCorrect(items);
        if (allCorrect) {
            return strengthOk && weakBlock != null
                    && weakBlock.contains("Không có. Chúc mừng bạn đã nắm vững toàn bộ kiến thức!");
        }

        boolean weakOk = countGroupedBullets(weakBlock) >= minBullets;
        return strengthOk && weakOk;
    }

    private long countGroupedBullets(String block) {
        if (block == null || block.isBlank()) return 0;
        return Arrays.stream(block.split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> GROUP_BULLET.matcher(s).matches())
                .count();
    }

    private boolean containsPlaceholder(String text) {
        String t = text == null ? "" : text;
        return t.contains("...") || t.contains("…") || t.contains("(Chưa có)") || t.contains("(Chưa xác định)");
    }

    private boolean hasHeader(String text, String header) {
        return indexOfIgnoreCase(text, header) >= 0;
    }

    private String extractBlock(String text, String header, String nextHeader) {
        int i = indexOfIgnoreCase(text, header);
        if (i < 0) return "";
        String tail = text.substring(i + header.length()).trim();

        // remove leading ":" if AI inserted
        if (tail.startsWith(":")) tail = tail.substring(1).trim();

        if (nextHeader == null) return tail;

        int j = indexOfIgnoreCase(tail, nextHeader);
        if (j < 0) return tail;
        return tail.substring(0, j).trim();
    }

    private int indexOfIgnoreCase(String text, String needle) {
        if (text == null || needle == null) return -1;
        return text.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private boolean equalsIgnoreCaseLine(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    // =========================================================
    // Rule-based fallback (NEW FORMAT)
    // =========================================================
    private String buildRuleBasedFeedback(String userFullName, int scorePct, List<AttemptReviewItemResponse> items) {
        String name = (userFullName == null || userFullName.isBlank()) ? "bạn" : userFullName.trim();
        List<AttemptReviewItemResponse> safe = items == null ? List.of() : items;

        int total = 0;
        int correct = 0;

        int totalMcq = 0, correctMcq = 0;
        int totalEssay = 0, weakEssay = 0;

        for (AttemptReviewItemResponse it : safe) {
            if (it == null) continue;
            total++;
            boolean ok = computeIsCorrect(it);
            if (ok) correct++;

            if (it.getQuestionType() == QuestionType.MCQ) {
                totalMcq++;
                if (ok) correctMcq++;
            } else if (it.getQuestionType() == QuestionType.ESSAY) {
                totalEssay++;
                int sc = it.getScore() == null ? 0 : it.getScore();
                int mx = it.getMaxScore() == null ? 0 : it.getMaxScore();
                if (mx > 0 && sc < Math.ceil(mx * 0.7)) weakEssay++;
            }
        }

        boolean allCorrect = total > 0 && correct == total;

        StringBuilder sb = new StringBuilder();
        sb.append("Chào ").append(name).append(",\n\n");
        sb.append("Điểm mạnh\n");
        sb.append("* **Tổng quan kết quả:** ")
                .append("Bạn đã hoàn thành bài và đạt ").append(scorePct).append("/100. ")
                .append("Điều này cho thấy bạn đã nắm được một phần kiến thức cốt lõi và biết cách áp dụng vào câu hỏi. ")
                .append("Hãy giữ nhịp làm bài ổn định để duy trì phong độ.\n");

        if (totalMcq > 0) {
            sb.append("* **Trắc nghiệm (MCQ):** ")
                    .append("Bạn làm đúng ").append(correctMcq).append("/").append(totalMcq).append(" câu. ")
                    .append("Các câu đúng phản ánh bạn nhận diện được keyword và chọn đáp án phù hợp với kiến thức nền. ")
                    .append("Tiếp tục luyện thêm để tăng độ chắc khi gặp bẫy phương án nhiễu.\n");
        }

        if (totalEssay > 0 && weakEssay == 0) {
            sb.append("* **Tự luận (Essay):** ")
                    .append("Phần tự luận của bạn khá tốt, thể hiện khả năng giải thích và trình bày theo ý chính. ")
                    .append("Bạn nên tiếp tục duy trì cách viết có cấu trúc (ý chính → giải thích → ví dụ). ")
                    .append("Điều này rất quan trọng để đạt điểm cao ổn định.\n");
        }

        sb.append("\nĐiểm yếu\n");
        if (allCorrect) {
            sb.append("Không có. Chúc mừng bạn đã nắm vững toàn bộ kiến thức!");
            return sb.toString().trim();
        }

        if (totalMcq > 0 && correctMcq < totalMcq) {
            sb.append("* **Nhận diện keyword & bẫy đáp án:** ")
                    .append("Bạn bị sai một số câu trắc nghiệm, thường do bỏ sót keyword hoặc nhầm điều kiện đúng/sai của khái niệm. ")
                    .append("Khi làm MCQ, hãy gạch ra 1-2 keyword quyết định và đối chiếu trực tiếp với lựa chọn trước khi chọn đáp án. ")
                    .append("Nếu 2 phương án gần giống nhau, hãy hỏi: “điều kiện nào khiến phương án này đúng?”.\n");
        }

        if (totalEssay > 0 && weakEssay > 0) {
            sb.append("* **Lập luận & keywords trong tự luận:** ")
                    .append("Một số câu tự luận còn thiếu ý trọng tâm hoặc thiếu từ khóa quan trọng, nên điểm chưa đạt ngưỡng tốt. ")
                    .append("Bạn cần nắm bản chất: định nghĩa ngắn + cơ chế hoạt động + 1 ví dụ ngắn (nếu phù hợp). ")
                    .append("Hãy viết theo checklist: (keyword bắt buộc) → (giải thích) → (minh họa).\n");
        }

        if (total == 0) {
            sb.append("* **Dữ liệu bài làm chưa đủ:** ")
                    .append("Hiện chưa có đủ dữ liệu câu trả lời để phân tích sâu. ")
                    .append("Hãy thử nộp lại với đầy đủ đáp án để hệ thống nhận xét chính xác hơn. ")
                    .append("Sau đó bạn có thể xem lại để rút ra lỗi sai lặp lại.\n");
        }

        return sb.toString().trim();
    }

    // =========================================================
    // Helpers
    // =========================================================
    private boolean computeIsCorrect(AttemptReviewItemResponse it) {
        if (it == null) return false;

        if (it.getIsCorrect() != null) return Boolean.TRUE.equals(it.getIsCorrect());

        if (it.getQuestionType() == QuestionType.MCQ) {
            String sel = safeStr(it.getSelectedAnswer()).trim();
            String right = safeStr(it.getCorrectAnswer()).trim();
            return !sel.isBlank() && sel.equalsIgnoreCase(right);
        }

        Integer sc = it.getScore() == null ? 0 : it.getScore();
        Integer mx = it.getMaxScore() == null ? 0 : it.getMaxScore();
        return mx > 0 && sc >= mx; // perfect only
    }

    private boolean isAllCorrect(List<AttemptReviewItemResponse> items) {
        if (items == null || items.isEmpty()) return false;
        for (AttemptReviewItemResponse it : items) {
            if (it == null) continue;
            if (!computeIsCorrect(it)) return false;
        }
        return true;
    }

    private String safeTrim(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() <= max) return t;
        return t.substring(0, max);
    }

    private String safeStr(String s) {
        return s == null ? "" : s;
    }
}
