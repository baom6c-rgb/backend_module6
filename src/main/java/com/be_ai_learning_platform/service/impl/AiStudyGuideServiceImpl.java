package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.AI.GeminiResponsesClient;
import com.be_ai_learning_platform.service.AiStudyGuideService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generate "Hướng dẫn ôn tập" (Study Guide) as a SEPARATE AI request.
 *
 * IMPORTANT:
 * - AI output is often messy (broken lines, zero-width chars, annotations split by newline, etc.)
 * - This service sanitizes output so FE can render cleanly.
 */
@Service
public class AiStudyGuideServiceImpl implements AiStudyGuideService {

    private static final int MAX_STUDY_GUIDE_CHARS = 9000;

    private final GeminiResponsesClient responsesClient;

    public AiStudyGuideServiceImpl(GeminiResponsesClient responsesClient) {
        this.responsesClient = responsesClient;
    }

    @Override
    public String generateStudyGuide(String userFullName, String userResultJson) {
        String name = (userFullName == null || userFullName.isBlank()) ? "bạn" : userFullName.trim();
        String json = (userResultJson == null) ? "" : userResultJson.trim();

        String prompt = buildStudyGuidePrompt(name, json);

        String raw = "";
        try {
            raw = responsesClient.generateText(prompt);
        } catch (Exception ignore) {
        }

        raw = safeTrim(raw, MAX_STUDY_GUIDE_CHARS);
        raw = stripBackticks(raw);

        // ✅ sanitize low-level noise first (zero-width, single-letter lines, etc.)
        raw = sanitizeStudyGuideText(raw);

        // ✅ normalize section-specific broken lines (concepts/vocab) BEFORE returning
        raw = normalizeStudyGuideText(raw);

        // Must-have header (outer)
        if (!containsIgnoreCase(raw, "Gợi ý ôn tập:")) {
            raw = ("Gợi ý ôn tập:\n" + raw).trim();
        }

        // Cấm placeholder lộ
        if (containsPlaceholder(raw)) {
            return fallbackStudyGuide(userFullName);
        }

        // Nếu quá ngắn -> fallback
        if (raw.isBlank() || raw.length() < 200) {
            return fallbackStudyGuide(userFullName);
        }

        return raw.trim();
    }

    @Override
    public String fallbackStudyGuide(String userFullName) {
        String name = (userFullName == null || userFullName.isBlank()) ? "bạn" : userFullName.trim();

        return ("""
Gợi ý ôn tập:
Tiêu đề: Hướng dẫn ôn tập cá nhân hóa
Môn học: Lập trình
Chủ đề: Ôn theo câu sai
Tóm tắt: Bạn đã hoàn thành bài và thể hiện nỗ lực học tập. Những câu làm đúng cho thấy bạn nắm được một phần kiến thức nền. Tuy nhiên, các câu sai đang chỉ ra một số lỗ hổng về khái niệm hoặc cách áp dụng trong tình huống cụ thể. Hãy tập trung xem lại các câu sai, đối chiếu với đáp án đúng và tự giải thích “vì sao đúng / vì sao sai”. Khi ôn, ưu tiên nắm chắc định nghĩa, điều kiện áp dụng, và làm 1–2 ví dụ nhỏ để kiểm chứng. Bạn hoàn toàn có thể cải thiện nhanh nếu ôn theo lỗi sai lặp lại và làm retest có mục tiêu.

Gợi ý ôn tập:
- Mở “Xem lại đáp án”, ghi lại 3 lỗi sai quan trọng nhất và lý do sai.
- Với mỗi lỗi, viết 1 quy tắc ngắn: “Nếu gặp dạng này, mình sẽ kiểm tra … trước”.
- Làm lại (Retest) sau cooldown và so sánh: lỗi nào đã hết, lỗi nào vẫn lặp lại.

Các khái niệm chính:
- Ôn lại định nghĩa, điều kiện áp dụng và ví dụ tối thiểu cho từng ý sai.

Danh sách từ vựng:
- keyword: từ khóa quan trọng trong đáp án.
- distractor: phương án nhiễu trong câu trắc nghiệm.
- rubric: tiêu chí chấm điểm cho tự luận.
- analysis: giải thích/nhận xét cho đáp án.

Câu hỏi ôn tập:
- Vì sao đáp án đúng là đúng trong câu sai nhiều nhất của %s?
- Điều kiện nào làm các lựa chọn còn lại trở nên sai?
- Với câu tự luận, bạn đã thiếu keyword nào quan trọng?
- Viết lại 1 ví dụ ngắn áp dụng đúng kiến thức vừa sai.
- Lần sau gặp dạng này, bạn sẽ kiểm tra điều gì đầu tiên?
""".formatted(name)).trim();
    }

    private String buildStudyGuidePrompt(String name, String userResultJson) {
        return ("""
Bạn là một trợ giảng AI chuyên nghiệp, tận tâm và có kiến thức sâu rộng về lĩnh vực lập trình và công nghệ ở cấp độ đại học.

NHIỆM VỤ:
Dựa trên dữ liệu kết quả bài kiểm tra (Quiz) của học viên ở dạng JSON bên dưới, hãy phân tích và tạo ra một "Tài liệu Hướng dẫn ôn tập cá nhân hóa" nhằm giúp học viên củng cố kiến thức và hiểu sâu bản chất vấn đề.

YÊU CẦU QUAN TRỌNG:
- BẮT ĐẦU bằng đúng 1 câu chào: "Chào %s,"
- Sau đó CHỈ trả về đúng khối "Gợi ý ôn tập" theo format dưới đây. KHÔNG thêm mục khác.

FORMAT BẮT BUỘC (giữ đúng tên mục và dấu ":" như bên dưới):
Gợi ý ôn tập:
Tiêu đề: ...
Môn học: ...
Chủ đề: ...
Tóm tắt: ...
Gợi ý ôn tập:
- ...
- ...
- ...

Các khái niệm chính:
Khái niệm 1: Giải thích chi tiết, ngắn gọn, dễ hiểu. Nếu là code/kỹ thuật, đưa 1 ví dụ minh họa ngắn.
Khái niệm 2: ...

Danh sách từ vựng:
Từ khóa 1: Định nghĩa ngắn gọn.
Từ khóa 2: Định nghĩa ngắn gọn.

Câu hỏi ôn tập:
- ...
- ...
- ...
- ...
- ...

QUY TẮC BẮT BUỘC:
- KHÔNG được dùng placeholder như "...", "…", "(Chưa có)", "(Chưa xác định)".
- "Tóm tắt": viết 1 đoạn văn khoảng 100-150 từ; khen phần đúng (isCorrect:true) và chỉ ra lỗ hổng dựa trên câu sai (isCorrect:false); giọng văn khích lệ, xây dựng.
- Phần "Gợi ý ôn tập:" phải có đúng 3 bullet "- " (cụ thể việc cần làm).
- "Câu hỏi ôn tập" bắt buộc đúng 5 câu (mỗi câu 1 bullet).
- "Các khái niệm chính": MỖI khái niệm trên 1 dòng theo format "Tên: Giải thích...". Không dùng bullet cho phần này.
- "Danh sách từ vựng": MỖI từ vựng trên 1 dòng theo format "Từ: Định nghĩa...". Không dùng bullet cho phần này.
- KHÔNG chèn xuống dòng giữa các ký tự trong cùng 1 từ. Chỉ xuống dòng khi sang mục hoặc sang item mới.
- Tuyệt đối KHÔNG dùng ký tự backtick: `
- Không markdown, không in đậm, không đánh số.
- Không bịa nội dung ngoài dữ liệu JSON.

DỮ LIỆU ĐẦU VÀO (JSON):
%s
""".formatted(name, userResultJson == null ? "" : userResultJson)).trim();
    }

    /**
     * Sanitize AI output to avoid "broken words" rendering in FE:
     * - Remove zero-width chars
     * - Normalize newlines
     * - Join annotations split by newline (e.g., "@\nRestController" -> "@RestController")
     * - Fix words broken into single-letter lines (e.g., "V\ní\ndụ" -> "Ví dụ")
     * - Join continuation lines inside the same item (without destroying section boundaries)
     */
    private String sanitizeStudyGuideText(String raw) {
        if (raw == null) return "";
        String t = raw;

        // normalize newlines
        t = t.replace("\r\n", "\n").replace("\r", "\n");

        // remove zero-width chars that often cause weird wrapping
        t = t.replaceAll("[\\u200B\\u200C\\u200D\\uFEFF]", "");

        // join annotations split by newline: "@\nRestController" -> "@RestController"
        t = t.replaceAll("@\\s*\\n\\s*(\\w)", "@$1");

        String[] lines = t.split("\n", -1);
        List<String> out = new ArrayList<>();

        String pendingChars = ""; // buffer for single-letter lines (V, í, d...)
        String prev = null;

        for (String line : lines) {
            String l = line == null ? "" : line.trim();

            // keep blank line as separator, but flush pending chars first
            if (l.isBlank()) {
                if (!pendingChars.isBlank()) {
                    out.add(pendingChars);
                    pendingChars = "";
                }
                out.add("");
                prev = "";
                continue;
            }

            // If a line is just 1 letter -> likely broken word
            if (looksLikeSingleCharLine(l)) {
                pendingChars += l;
                continue;
            }

            // attach pending chars to the current line
            if (!pendingChars.isBlank()) {
                if (startsLikeWordContinuation(l)) {
                    l = pendingChars + l;
                } else {
                    l = pendingChars + " " + l;
                }
                pendingChars = "";
            }

            // join continuation lines within same item to reduce random breaks
            if (prev != null && !prev.isBlank()) {
                boolean prevIsHeader = isSectionHeader(prev);
                boolean curIsHeader = isSectionHeader(l);

                boolean prevIsBullet = prev.startsWith("- ");
                boolean curIsBullet = l.startsWith("- ");

                boolean curLooksNewKeyLine = looksLikeKeyValueLine(l); // "X: Y"

                // Join when:
                // - not headers
                // - not bullet lines
                // - and current is NOT a new key/value item (to keep concepts/vocab each on its own line)
                if (!prevIsHeader && !curIsHeader && !curIsBullet && !prevIsBullet && !curLooksNewKeyLine) {
                    String merged = (prev + " " + l).replaceAll("\\s{2,}", " ").trim();
                    out.set(out.size() - 1, merged);
                    prev = merged;
                    continue;
                }
            }

            out.add(l);
            prev = l;
        }

        if (!pendingChars.isBlank()) {
            out.add(pendingChars);
        }

        String rebuilt = String.join("\n", out);
        rebuilt = rebuilt.replaceAll("\n{3,}", "\n\n");

        return rebuilt.trim();
    }

    /**
     * ✅ Normalize section "Các khái niệm chính" & "Danh sách từ vựng"
     *
     * Goal:
     * - If AI outputs broken lines between words (Controller / trong / Spring / MVC): join them.
     * - Keep each item in concepts/vocab on ONE line in "Tên: ..." format.
     * - Keep section boundaries and other sections intact.
     */
    private String normalizeStudyGuideText(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String t = raw;

        // normalize parentheses line breaks: "( \n" -> "(" ; "\n )" -> ")"
        t = t.replaceAll("\\(\\s*\\n\\s*", "(");
        t = t.replaceAll("\\n\\s*\\)", ")");

        // ensure @ never stands alone: "@\nRestController" already fixed in sanitize, but keep robust
        t = t.replaceAll("@\\s*\\n\\s*(\\w)", "@$1");

        String[] lines = t.split("\n", -1);
        List<String> out = new ArrayList<>();

        boolean inConcepts = false;
        boolean inVocab = false;

        StringBuilder pendingItem = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String lineRaw = lines[i] == null ? "" : lines[i];
            String line = lineRaw.trim();

            // detect section headers
            if (isHeaderLine(line, "Các khái niệm chính:")) {
                flushPendingItem(out, pendingItem);
                inConcepts = true;
                inVocab = false;
                out.add("Các khái niệm chính:");
                continue;
            }
            if (isHeaderLine(line, "Danh sách từ vựng:")) {
                flushPendingItem(out, pendingItem);
                inConcepts = false;
                inVocab = true;
                out.add("Danh sách từ vựng:");
                continue;
            }
            if (isAnyOtherHeader(line)) {
                flushPendingItem(out, pendingItem);
                inConcepts = false;
                inVocab = false;
                out.add(line);
                continue;
            }

            // keep empty lines, but flush pending first
            if (line.isBlank()) {
                flushPendingItem(out, pendingItem);
                out.add("");
                continue;
            }

            // inside concepts/vocab: join broken lines until we have "X: Y"
            if (inConcepts || inVocab) {
                // if bullet sneaks in => treat as new line (but still flush pending)
                if (line.startsWith("- ") || line.startsWith("• ") || line.startsWith("* ")) {
                    flushPendingItem(out, pendingItem);
                    out.add(line); // keep bullet as-is
                    continue;
                }

                boolean hasColon = looksLikeKeyValueLine(line);

                if (hasColon) {
                    // this is a new item start; flush previous pending item then store this as complete item
                    flushPendingItem(out, pendingItem);
                    out.add(compactSpaces(line));
                    continue;
                }

                // no colon => likely continuation of previous line or broken wrapping BEFORE colon appears
                if (pendingItem.length() == 0) {
                    // start a pending item (maybe it will gain ":" later)
                    pendingItem.append(line);
                } else {
                    // join tokens safely
                    String sep = needsNoSpaceJoin(pendingItem.toString(), line) ? "" : " ";
                    pendingItem.append(sep).append(line);
                }
                continue;
            }

            // outside these sections => keep line as-is
            out.add(lineRaw.trim());
        }

        flushPendingItem(out, pendingItem);

        String rebuilt = String.join("\n", out);

        // final tidy
        rebuilt = rebuilt.replaceAll("[ \\t]{2,}", " ");
        rebuilt = rebuilt.replaceAll("\n{3,}", "\n\n");
        return rebuilt.trim();
    }

    private void flushPendingItem(List<String> out, StringBuilder pendingItem) {
        if (pendingItem == null || pendingItem.length() == 0) return;

        String item = pendingItem.toString().trim();
        pendingItem.setLength(0);

        if (!item.isBlank()) {
            out.add(compactSpaces(item));
        }
    }

    private String compactSpaces(String s) {
        if (s == null) return "";
        String t = s.replaceAll("\\s{2,}", " ").trim();
        // fix "( " and " )"
        t = t.replaceAll("\\(\\s+", "(");
        t = t.replaceAll("\\s+\\)", ")");
        return t;
    }

    private boolean needsNoSpaceJoin(String prev, String next) {
        if (prev == null || prev.isBlank() || next == null || next.isBlank()) return false;
        String p = prev.trim();
        String n = next.trim();
        // "@"+ "RestController"
        if (p.endsWith("@")) return true;
        // "Controller (" + "trong" => keep space
        // "Spring" + "MVC):" => keep space
        // "(" + "@" => no space
        if (p.endsWith("(")) return true;
        if (n.startsWith(")")) return true;
        if (n.startsWith(",")) return true;
        if (n.startsWith(".")) return true;
        if (n.startsWith(":")) return true;
        if (n.startsWith(";")) return true;
        return false;
    }

    private boolean isHeaderLine(String line, String header) {
        if (line == null) return false;
        String a = line.trim().toLowerCase(Locale.ROOT).replaceAll("[:：]\\s*$", "");
        String b = header.trim().toLowerCase(Locale.ROOT).replaceAll("[:：]\\s*$", "");
        return a.equals(b);
    }

    private boolean isAnyOtherHeader(String line) {
        // treat these as section boundaries so we stop joining concepts/vocab
        if (line == null) return false;
        String s = line.trim().toLowerCase(Locale.ROOT);
        s = s.replaceAll("[:：]\\s*$", "");
        return s.equals("gợi ý ôn tập")
                || s.equals("câu hỏi ôn tập")
                || s.startsWith("tiêu đề")
                || s.startsWith("môn học")
                || s.startsWith("chủ đề")
                || s.startsWith("tóm tắt")
                || s.equals("gợi ý ôn tập"); // duplicate safe
    }

    private boolean looksLikeSingleCharLine(String l) {
        if (l == null) return false;
        String s = l.trim();
        if (s.isEmpty()) return false;
        if (s.length() > 2) return false;
        if (s.equals("-") || s.equals("*") || s.equals("•")) return false;
        return s.length() == 1 && Character.isLetter(s.charAt(0));
    }

    private boolean startsLikeWordContinuation(String l) {
        if (l == null || l.isBlank()) return false;
        char c = l.charAt(0);
        return Character.isLetterOrDigit(c) || c == '@' || c == '(' || c == '/' || c == '_';
    }

    private boolean looksLikeKeyValueLine(String l) {
        if (l == null) return false;
        String s = l.trim();
        int idx = s.indexOf(':');
        if (idx <= 0) return false;
        if (idx > 120) return false;
        if (idx <= 2 && s.matches("^\\d{1,2}:\\d{2}.*")) return false;
        // disallow urls with ":" early? (keep simple)
        return true;
    }

    private boolean isSectionHeader(String line) {
        if (line == null) return false;
        String s = line.trim().toLowerCase(Locale.ROOT);
        s = s.replaceAll("[:：]\\s*$", "");
        return s.equals("gợi ý ôn tập")
                || s.equals("các khái niệm chính")
                || s.equals("danh sách từ vựng")
                || s.equals("câu hỏi ôn tập")
                || s.startsWith("tiêu đề")
                || s.startsWith("môn học")
                || s.startsWith("chủ đề")
                || s.startsWith("tóm tắt");
    }

    private String safeTrim(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() <= max) return t;
        return t.substring(0, max).trim();
    }

    private String stripBackticks(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```[a-zA-Z]*\\s*", "");
            t = t.replaceFirst("\\s*```\\s*$", "");
            t = t.trim();
        }
        return t.replace("`", "").trim();
    }

    private boolean containsIgnoreCase(String text, String needle) {
        if (text == null || needle == null) return false;
        return text.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private boolean containsPlaceholder(String text) {
        if (text == null) return false;
        return text.contains("...") || text.contains("…") || text.contains("(Chưa có)") || text.contains("(Chưa xác định)");
    }
}