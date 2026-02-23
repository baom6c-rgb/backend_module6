package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.AI.GeminiStructuredClient;
import com.be_ai_learning_platform.dto.response.TopicOptionResponse;
import com.be_ai_learning_platform.entity.LearningMaterial;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.MaterialStatus;
import com.be_ai_learning_platform.repository.LearningMaterialRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.TopicSelectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class TopicSelectionServiceImpl implements TopicSelectionService {

    /**
     * If extracted text is short enough, assume user sent ONE lesson/topic.
     * Still can be multi, but this reduces unnecessary AI calls.
     */
    private static final int SMALL_TEXT_ASSUME_SINGLE_CHARS = 1200;

    /**
     * For topic detection prompt, we don't need the whole document.
     * Keep it consistent with question generation.
     */
    private static final int MAX_DETECT_CHARS = 6000;

    /**
     * store focusText server-side; FE only needs title/summary.
     */
    private static final int MAX_FOCUS_TEXT_CHARS = 2500;

    /**
     * If we can confidently detect multiple sub-topics by heuristic,
     * we avoid an AI call and immediately ask user to choose.
     */
    private static final int HEURISTIC_EXCERPT_WINDOW_CHARS = 1400;

    private final UserRepository userRepo;
    private final LearningMaterialRepository materialRepo;
    private final GeminiStructuredClient ai;
    private final Cache<String, Object> practiceSessionCache;
    private final ObjectMapper om;

    public TopicSelectionServiceImpl(
            UserRepository userRepo,
            LearningMaterialRepository materialRepo,
            GeminiStructuredClient ai,
            Cache<String, Object> practiceSessionCache,
            ObjectMapper om
    ) {
        this.userRepo = userRepo;
        this.materialRepo = materialRepo;
        this.ai = ai;
        this.practiceSessionCache = practiceSessionCache;
        this.om = om;
    }

    @Override
    public TopicSelectionResult detectTopics(String currentEmail, Long materialId) {
        User me = userRepo.findByEmail(currentEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        LearningMaterial material = materialRepo.findByIdAndUser(materialId, me)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));

        if (material.getStatus() != MaterialStatus.EXTRACTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Material is not extracted yet");
        }

        String extracted = material.getExtractedText();
        if (extracted == null || extracted.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Extracted text is empty");
        }

        // 0) Strong heuristic for common “one lesson contains multiple sub-parts” cases
        // Example: Vòng lặp -> for / while / do-while
        HeuristicDetect hd = detectSubtopicsByHeuristic(extracted);
        if (hd != null && hd.options.size() >= 2) {
            String selectionToken = UUID.randomUUID().toString();

            TopicSelectionCacheData cacheData = new TopicSelectionCacheData();
            cacheData.userEmail = currentEmail;
            cacheData.materialId = materialId;
            cacheData.focusByTopicId = hd.focusById;

            practiceSessionCache.put(selectionKey(currentEmail, selectionToken), cacheData);

            TopicSelectionResult out = new TopicSelectionResult();
            out.isMulti = true;
            out.selectionToken = selectionToken;
            out.topics = hd.options;
            return out;
        }

        // 1) quick heuristic
        if (extracted.length() <= SMALL_TEXT_ASSUME_SINGLE_CHARS && !looksLikeMultiLesson(extracted)) {
            TopicSelectionResult r = new TopicSelectionResult();
            r.isMulti = false;
            r.topics = List.of();
            r.selectionToken = null;
            return r;
        }

        String trimmed = extracted.length() > MAX_DETECT_CHARS ? extracted.substring(0, MAX_DETECT_CHARS) : extracted;

        String prompt = buildDetectPrompt(trimmed);
        String contract = detectJsonContract();

        String json = ai.generateJson(prompt, contract);
        DetectResult dr;
        try {
            dr = om.readValue(json, DetectResult.class);
        } catch (Exception e) {
            // If AI returns invalid JSON, fall back to heuristic (safe default: single)
            TopicSelectionResult r = new TopicSelectionResult();
            r.isMulti = looksLikeMultiLesson(trimmed);
            r.topics = List.of();
            r.selectionToken = null;
            return r;
        }

        List<DetectTopic> topics = (dr == null || dr.topics == null) ? List.of() : dr.topics;
        boolean multi = Boolean.TRUE.equals(dr.isMulti) && topics.size() >= 2;

        TopicSelectionResult out = new TopicSelectionResult();
        out.isMulti = multi;

        if (!multi) {
            out.topics = List.of();
            out.selectionToken = null;
            return out;
        }

        String selectionToken = UUID.randomUUID().toString();
        List<TopicOptionResponse> options = new ArrayList<>();
        Map<String, String> focusById = new HashMap<>();

        int idx = 1;
        for (DetectTopic t : topics) {
            if (t == null) continue;
            String id = safeId(t.id);
            if (id.isBlank()) id = "T" + (idx++);

            TopicOptionResponse opt = new TopicOptionResponse();
            opt.setId(id);
            opt.setTitle(safeTrim(t.title, 120));
            opt.setSummary(safeTrim(t.summary, 240));
            opt.setKeywords(t.keywords == null ? List.of() : trimList(t.keywords, 8, 40));
            options.add(opt);

            String focus = safeTrim(t.focusText, MAX_FOCUS_TEXT_CHARS);
            if (focus.isBlank()) {
                // fallback: use title+summary if AI forgot focusText
                focus = (opt.getTitle() + "\n" + opt.getSummary()).trim();
            }
            focusById.put(id, focus);
        }

        if (options.size() < 2) {
            // Not enough options -> treat as single
            out.isMulti = false;
            out.topics = List.of();
            out.selectionToken = null;
            return out;
        }

        // store in cache
        TopicSelectionCacheData cacheData = new TopicSelectionCacheData();
        cacheData.userEmail = currentEmail;
        cacheData.materialId = materialId;
        cacheData.focusByTopicId = focusById;

        practiceSessionCache.put(selectionKey(currentEmail, selectionToken), cacheData);

        out.selectionToken = selectionToken;
        out.topics = options;
        return out;
    }

    @Override
    public String resolveFocusText(String currentEmail, String selectionToken, String topicId) {
        if (selectionToken == null || selectionToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "selectionToken is required");
        }
        if (topicId == null || topicId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "topicId is required");
        }

        Object obj = practiceSessionCache.getIfPresent(selectionKey(currentEmail, selectionToken));
        if (!(obj instanceof TopicSelectionCacheData data)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Topic selection expired. Please generate again.");
        }
        if (!Objects.equals(data.userEmail, currentEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Selection does not belong to current user");
        }

        String focus = data.focusByTopicId == null ? null : data.focusByTopicId.get(topicId);
        if (focus == null || focus.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found");
        }
        return focus;
    }

    @Override
    public Long resolveMaterialId(String currentEmail, String selectionToken) {
        if (selectionToken == null || selectionToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "selectionToken is required");
        }
        Object obj = practiceSessionCache.getIfPresent(selectionKey(currentEmail, selectionToken));
        if (!(obj instanceof TopicSelectionCacheData data)) {
            return null;
        }
        if (!Objects.equals(data.userEmail, currentEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Selection does not belong to current user");
        }
        return data.materialId;
    }

    // ===== helpers =====

    private String selectionKey(String email, String token) {
        return "topicSel:" + email + ":" + token;
    }

    private boolean looksLikeMultiLesson(String text) {
        if (text == null) return false;
        String t = text;
        // headings / chapters / lessons
        int hits = 0;
        String[] markers = new String[]{
                "Chương ", "Bài ", "Lesson ", "Chapter ",
                "## ", "# ",
                "I.", "II.", "III.", "IV.",
                "1.", "2.", "3.",
                "Mục ", "Phần ",
        };
        for (String m : markers) {
            int idx = 0;
            while ((idx = t.indexOf(m, idx)) >= 0) {
                hits++;
                idx += m.length();
                if (hits >= 3) return true;
            }
        }
        return false;
    }

    /**
     * Heuristic detector for common “one lesson contains multiple sub-parts”.
     * Current focus: loops (for / while / do-while / foreach) because this is a high-frequency case
     * and users often paste a whole "Vòng lặp" lesson.
     *
     * If we detect >= 2 loop types, we return NEED_TOPIC immediately (no AI call).
     */
    private HeuristicDetect detectSubtopicsByHeuristic(String extracted) {
        if (extracted == null || extracted.isBlank()) return null;

        String lower = extracted.toLowerCase(Locale.ROOT);

        boolean hasFor = lower.contains("for(") || lower.contains("for (");
        boolean hasWhile = lower.contains("while(") || lower.contains("while (");
        boolean hasDoWhile = lower.contains("do-while") || looksLikeDoWhile(lower);
        boolean hasForeach = lower.contains("foreach") || lower.contains("for-each") || lower.contains("enhanced for") || lower.contains("for each");

        int count = 0;
        if (hasFor) count++;
        if (hasWhile) count++;
        if (hasDoWhile) count++;
        if (hasForeach) count++;

        if (count < 2) return null;

        List<TopicOptionResponse> options = new ArrayList<>();
        Map<String, String> focusById = new HashMap<>();

        if (hasFor) {
            addHeuristicTopic(options, focusById,
                    "LOOP_FOR",
                    "Vòng lặp for",
                    "Dùng khi biết trước số lần lặp; thường có biến đếm và điều kiện dừng.",
                    List.of("for", "counter", "iteration"),
                    extracted,
                    firstIndexOfAny(lower, List.of("for(", "for (")));
        }
        if (hasWhile) {
            addHeuristicTopic(options, focusById,
                    "LOOP_WHILE",
                    "Vòng lặp while",
                    "Dùng khi chưa biết trước số lần lặp; lặp khi điều kiện còn đúng.",
                    List.of("while", "condition"),
                    extracted,
                    firstIndexOfAny(lower, List.of("while(", "while (")));
        }
        if (hasDoWhile) {
            addHeuristicTopic(options, focusById,
                    "LOOP_DO_WHILE",
                    "Vòng lặp do-while",
                    "Luôn chạy ít nhất 1 lần rồi mới kiểm tra điều kiện để lặp tiếp.",
                    List.of("do-while", "do", "while"),
                    extracted,
                    firstIndexOfAny(lower, List.of("do-while", "do{", "do {", "do\n", "do\r\n")));
        }
        if (hasForeach) {
            addHeuristicTopic(options, focusById,
                    "LOOP_FOREACH",
                    "Vòng lặp foreach (enhanced for)",
                    "Duyệt qua phần tử của mảng/collection; tránh thao tác chỉ số thủ công.",
                    List.of("foreach", "enhanced for", "collection"),
                    extracted,
                    firstIndexOfAny(lower, List.of("foreach", "for-each", "enhanced for", "for each")));
        }

        options = dedupeById(options);
        if (options.size() < 2) return null;

        HeuristicDetect out = new HeuristicDetect();
        out.options = options;
        out.focusById = focusById;
        return out;
    }

    private boolean looksLikeDoWhile(String lower) {
        // Detect "do { ... } while (...)" patterns (rough, but stable and cheap)
        int doIdx = lower.indexOf("do");
        while (doIdx >= 0) {
            int whileIdx = lower.indexOf("while", doIdx);
            if (whileIdx > doIdx && (whileIdx - doIdx) <= 300) {
                return true;
            }
            doIdx = lower.indexOf("do", doIdx + 2);
        }
        return false;
    }

    private int firstIndexOfAny(String lower, List<String> needles) {
        int best = -1;
        for (String n : needles) {
            if (n == null || n.isBlank()) continue;
            int idx = lower.indexOf(n);
            if (idx >= 0 && (best < 0 || idx < best)) best = idx;
        }
        return best;
    }

    private void addHeuristicTopic(
            List<TopicOptionResponse> options,
            Map<String, String> focusById,
            String id,
            String title,
            String summary,
            List<String> keywords,
            String originalText,
            int hitIndex
    ) {
        TopicOptionResponse opt = new TopicOptionResponse();
        opt.setId(id);
        opt.setTitle(title);
        opt.setSummary(summary);
        opt.setKeywords(keywords);
        options.add(opt);

        String focus = extractExcerpt(originalText, hitIndex, HEURISTIC_EXCERPT_WINDOW_CHARS);
        focus = safeTrim(compactWhitespace(focus), MAX_FOCUS_TEXT_CHARS);
        if (focus.isBlank()) {
            focus = (title + "\n" + summary).trim();
        }
        focusById.put(id, focus);
    }

    private String extractExcerpt(String text, int hitIndex, int windowChars) {
        if (text == null || text.isBlank()) return "";
        if (hitIndex < 0) {
            int end = Math.min(text.length(), Math.min(windowChars, MAX_FOCUS_TEXT_CHARS));
            return text.substring(0, end);
        }
        int start = Math.max(0, hitIndex - 400);
        int end = Math.min(text.length(), hitIndex + windowChars);
        return text.substring(start, end);
    }

    private String compactWhitespace(String s) {
        if (s == null) return "";
        String t = s.replace("\t", " ");
        // collapse 3+ blank lines
        t = t.replaceAll("\n{3,}", "\n\n");
        // collapse multiple spaces
        t = t.replaceAll(" {2,}", " ");
        return t.trim();
    }

    private List<TopicOptionResponse> dedupeById(List<TopicOptionResponse> in) {
        if (in == null || in.isEmpty()) return List.of();
        Map<String, TopicOptionResponse> map = new LinkedHashMap<>();
        for (TopicOptionResponse o : in) {
            if (o == null || o.getId() == null) continue;
            map.putIfAbsent(o.getId(), o);
        }
        return new ArrayList<>(map.values());
    }

    private String buildDetectPrompt(String trimmedMaterial) {
        return """
Bạn là trợ giảng. Hãy đọc học liệu dưới đây và xác định xem nội dung có thể chia thành NHIỀU TIỂU MỤC (sub-topic) để làm bài riêng hay không.

Ví dụ minh hoạ (chỉ là ví dụ):
- Nếu học liệu nói về "Vòng lặp" và có cả for / while / do-while => đây là nhiều tiểu mục.
- Nếu chỉ nói về duy nhất vòng lặp for => chỉ 1 tiểu mục.

YÊU CẦU:
- Nếu có nhiều tiểu mục: liệt kê 2 đến 8 topic rõ ràng để học viên chọn.
- Mỗi topic cần có: id, title, summary ngắn, keywords, và focusText (đoạn TRÍCH từ học liệu để tạo câu hỏi tập trung).
- Nếu chỉ có 1 tiểu mục: trả isMulti=false và topics=[].

HỌC LIỆU:
""" + trimmedMaterial + "\n";
    }

    private String detectJsonContract() {
        return """
CHỈ TRẢ VỀ JSON THUẦN – KHÔNG markdown, KHÔNG giải thích.
CHỈ 1 JSON object duy nhất.

FORMAT:
{
  "isMulti": true,
  "topics": [
    {
      "id": "T1",
      "title": "...",
      "summary": "...",
      "keywords": ["..."],
      "focusText": "..."
    }
  ]
}

RULES:
- isMulti ∈ {true,false}
- Nếu isMulti=false => topics phải là []
- Nếu isMulti=true => topics có 2..8 phần tử
- focusText tối đa ~2000 ký tự, PHẢI TRÍCH từ học liệu, KHÔNG bịa nội dung ngoài học liệu
- KẾT THÚC OUTPUT bằng dấu }
""";
    }

    private String safeTrim(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() <= max) return t;
        return t.substring(0, max).trim();
    }

    private String safeId(String s) {
        if (s == null) return "";
        return s.trim();
    }

    private List<String> trimList(List<String> in, int maxSize, int maxLen) {
        if (in == null || in.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (String s : in) {
            if (s == null) continue;
            String t = s.trim();
            if (t.isBlank()) continue;
            if (t.length() > maxLen) t = t.substring(0, maxLen).trim();
            out.add(t);
            if (out.size() >= maxSize) break;
        }
        return out;
    }

    // ===== internal JSON mapping =====
    private static class DetectResult {
        public Boolean isMulti;
        public List<DetectTopic> topics;
    }

    private static class DetectTopic {
        public String id;
        public String title;
        public String summary;
        public List<String> keywords;
        public String focusText;
    }

    private static class TopicSelectionCacheData {
        public String userEmail;
        public Long materialId;
        public Map<String, String> focusByTopicId;
    }

    private static class HeuristicDetect {
        public List<TopicOptionResponse> options = new ArrayList<>();
        public Map<String, String> focusById = new HashMap<>();
    }
}
