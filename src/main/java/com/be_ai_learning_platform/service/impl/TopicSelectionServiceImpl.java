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
import java.util.regex.Pattern;

@Service
public class TopicSelectionServiceImpl implements TopicSelectionService {

    private static final int SMALL_TEXT_ASSUME_SINGLE_CHARS = 1200;
    private static final int MAX_DETECT_CHARS = 6000;
    private static final int MAX_FOCUS_TEXT_CHARS = 2500;
    private static final int MAX_MULTI_FOCUS_TEXT_CHARS = 6000;

    // Objective bullets
    private static final int BULLET_SECTION_SCAN_LIMIT = 5000;
    private static final int MIN_OBJECTIVE_BULLETS_TO_TRIGGER = 4;
    private static final int MAX_OBJECTIVE_OPTIONS = 12;
    private static final int MAX_BULLET_LINE_LEN = 240;

    // Section headings
    private static final int MAX_HEADING_LINES_SCAN = 600;
    private static final int MAX_SECTIONS = 10;
    private static final int MAX_SECTION_LINES = 140;

    private static final Pattern BULLET_LINE = Pattern.compile(
            "^\\s*(?:[•\\-*–—]|\\d+\\.|\\(?[a-zA-Z]\\)|\\(?[ivxIVX]+\\)|\\([0-9]+\\))\\s+.+$"
    );

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

        // =========================================================
        // RULE 1: Objectives/outcomes bullets => choose 1 bullet
        // =========================================================
        HeuristicDetect obj = detectObjectiveBullets(extracted);
        if (obj != null && obj.options.size() >= 2) {
            return cacheAndReturnMulti(currentEmail, materialId, obj);
        }

        // =========================================================
        // RULE 2: Small file => generate directly
        // =========================================================
        if (extracted.length() <= SMALL_TEXT_ASSUME_SINGLE_CHARS && !looksLikeMultiLesson(extracted)) {
            TopicSelectionResult r = new TopicSelectionResult();
            r.isMulti = false;
            r.topics = List.of();
            r.selectionToken = null;
            return r;
        }

        // =========================================================
        // RULE 3: Big file => split by headings/sections (generic)
        // =========================================================
        HeuristicDetect sec = detectSectionsByHeadings(extracted);
        if (sec != null && sec.options.size() >= 2) {
            return cacheAndReturnMulti(currentEmail, materialId, sec);
        }

        // =========================================================
        // RULE 4: fallback AI split 2..8 parts
        // =========================================================
        String trimmed = extracted.length() > MAX_DETECT_CHARS
                ? extracted.substring(0, MAX_DETECT_CHARS)
                : extracted;

        String prompt = buildDetectPrompt(trimmed);
        String contract = detectJsonContract();

        String json = ai.generateJson(prompt, contract);
        DetectResult dr;
        try {
            dr = om.readValue(json, DetectResult.class);
        } catch (Exception e) {
            TopicSelectionResult r = new TopicSelectionResult();
            r.isMulti = looksLikeMultiLesson(trimmed);
            r.topics = List.of();
            r.selectionToken = null;
            return r;
        }

        List<DetectTopic> topics = (dr == null || dr.topics == null) ? List.of() : dr.topics;
        boolean multi = Boolean.TRUE.equals(dr.isMulti) && topics.size() >= 2;

        if (!multi) {
            TopicSelectionResult r = new TopicSelectionResult();
            r.isMulti = false;
            r.topics = List.of();
            r.selectionToken = null;
            return r;
        }

        List<TopicOptionResponse> options = new ArrayList<>();
        Map<String, String> focusById = new LinkedHashMap<>();

        int idx = 1;
        for (DetectTopic t : topics) {
            if (t == null) continue;

            String id = safeId(t.id);
            if (id.isBlank()) id = "T" + (idx++);

            TopicOptionResponse opt = new TopicOptionResponse();
            opt.setId(id);
            opt.setTitle(safeTrim(t.title, 160));
            opt.setSummary(safeTrim(t.summary, 260));
            opt.setKeywords(t.keywords == null ? List.of() : trimList(t.keywords, 8, 40));
            options.add(opt);

            String focus = safeTrim(t.focusText, MAX_FOCUS_TEXT_CHARS);
            if (focus.isBlank()) {
                focus = (opt.getTitle() + "\n" + opt.getSummary()).trim();
            }
            focusById.put(id, focus);
        }

        options = dedupeById(options);
        if (options.size() < 2) {
            TopicSelectionResult r = new TopicSelectionResult();
            r.isMulti = false;
            r.topics = List.of();
            r.selectionToken = null;
            return r;
        }

        HeuristicDetect fromAi = new HeuristicDetect();
        fromAi.options = options;
        fromAi.focusById = focusById;

        return cacheAndReturnMulti(currentEmail, materialId, fromAi);
    }

    private TopicSelectionResult cacheAndReturnMulti(String currentEmail, Long materialId, HeuristicDetect hd) {
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
    public String resolveFocusText(String currentEmail, String selectionToken, List<String> topicIds) {
        if (selectionToken == null || selectionToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "selectionToken is required");
        }
        if (topicIds == null || topicIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "topicIds is required");
        }

        Object obj = practiceSessionCache.getIfPresent(selectionKey(currentEmail, selectionToken));
        if (!(obj instanceof TopicSelectionCacheData data)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Topic selection expired. Please generate again.");
        }
        if (!Objects.equals(data.userEmail, currentEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Selection does not belong to current user");
        }

        // Deduplicate while keeping order
        LinkedHashSet<String> uniq = new LinkedHashSet<>();
        for (String id : topicIds) {
            if (id != null && !id.isBlank()) uniq.add(id.trim());
        }
        if (uniq.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "topicIds is required");
        }

        StringBuilder sb = new StringBuilder();
        int added = 0;

        for (String id : uniq) {
            String focus = data.focusByTopicId == null ? null : data.focusByTopicId.get(id);
            if (focus == null || focus.isBlank()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found: " + id);
            }

            if (sb.length() > 0) sb.append("\n\n---\n\n");
            sb.append(focus.trim());
            added++;

            if (sb.length() >= MAX_MULTI_FOCUS_TEXT_CHARS) break;
        }

        String merged = sb.toString().trim();
        if (merged.length() > MAX_MULTI_FOCUS_TEXT_CHARS) {
            merged = merged.substring(0, MAX_MULTI_FOCUS_TEXT_CHARS);
        }

        // Nếu chọn quá nhiều, vẫn cho chạy nhưng clamp
        if (added < uniq.size()) {
            merged = (merged + "\n\n(Lưu ý: Bạn đã chọn nhiều phần, hệ thống chỉ lấy phần đầu để tránh quá dài.)").trim();
        }
        return merged;
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

    // ===== keys =====
    private String selectionKey(String email, String token) {
        return "topicSel:" + email + ":" + token;
    }

    // =========================================================
    // Generic: objective/outcome bullets (all topics)
    // =========================================================
    private HeuristicDetect detectObjectiveBullets(String extracted) {
        String scan = extracted.length() > BULLET_SECTION_SCAN_LIMIT
                ? extracted.substring(0, BULLET_SECTION_SCAN_LIMIT)
                : extracted;

        String[] lines = scan.split("\\r?\\n");

        int headingLine = findObjectiveHeadingLine(lines);
        if (headingLine < 0) return null;

        List<String> bullets = collectBulletsAfterHeading(lines, headingLine);
        bullets = dedupeStrings(bullets);

        if (bullets.size() < MIN_OBJECTIVE_BULLETS_TO_TRIGGER) return null;

        List<TopicOptionResponse> options = new ArrayList<>();
        Map<String, String> focusById = new LinkedHashMap<>();

        int i = 1;
        for (String b : bullets) {
            if (options.size() >= MAX_OBJECTIVE_OPTIONS) break;

            String id = "OBJ_" + i++;
            String title = safeTrim(b, 180);

            TopicOptionResponse opt = new TopicOptionResponse();
            opt.setId(id);
            opt.setTitle(title);
            opt.setSummary("Chọn 1 mục tiêu để tạo đề tập trung đúng yêu cầu bạn muốn luyện.");
            opt.setKeywords(extractKeywordsLight(title));
            options.add(opt);

            focusById.put(id, safeTrim("Mục tiêu cần đạt: " + title, MAX_FOCUS_TEXT_CHARS));
        }

        options = dedupeById(options);
        if (options.size() < 2) return null;

        HeuristicDetect out = new HeuristicDetect();
        out.options = options;
        out.focusById = focusById;
        return out;
    }

    private int findObjectiveHeadingLine(String[] lines) {
        if (lines == null || lines.length == 0) return -1;

        List<String> keys = List.of(
                "mục tiêu", "muc tieu","mụctiêu",
                "mục đích", "muc dich",
                "kết quả", "ket qua",
                "đầu ra", "dau ra",
                "chuẩn đầu ra", "chuan dau ra",
                "objectives", "objective",
                "learning outcomes", "outcomes",
                "goals", "goal",
                "yêu cầu", "yeu cau"
        );

        for (int i = 0; i < Math.min(lines.length, 80); i++) {
            String raw = lines[i] == null ? "" : lines[i].trim();
            if (raw.isBlank()) continue;
            if (raw.length() > 90) continue;
            if (BULLET_LINE.matcher(raw).matches()) continue;

            String lower = raw.toLowerCase(Locale.ROOT);
            for (String k : keys) {
                if (lower.contains(k)) return i;
            }
        }
        return -1;
    }

    private List<String> collectBulletsAfterHeading(String[] lines, int headingLine) {
        List<String> out = new ArrayList<>();
        int blankCount = 0;

        for (int i = headingLine + 1; i < Math.min(lines.length, headingLine + 80); i++) {
            String raw = lines[i] == null ? "" : lines[i].trim();

            if (raw.isBlank()) {
                blankCount++;
                if (blankCount >= 2 && out.size() >= 2) break;
                continue;
            }
            blankCount = 0;

            // stop at next heading-like line
            if (!BULLET_LINE.matcher(raw).matches()) {
                if (looksLikeHeading(raw)) break;
                continue;
            }

            if (raw.length() > MAX_BULLET_LINE_LEN) continue;

            String cleaned = stripBulletPrefix(raw).replaceAll("\\s+", " ").trim();
            if (cleaned.length() < 8) continue;

            out.add(cleaned);
        }

        return out;
    }

    private String stripBulletPrefix(String line) {
        if (line == null) return "";
        String t = line.trim();
        t = t.replaceFirst("^\\s*[•\\-*–—]\\s+", "");
        t = t.replaceFirst("^\\s*\\d+\\.\\s+", "");
        t = t.replaceFirst("^\\s*\\([0-9]+\\)\\s+", "");
        t = t.replaceFirst("^\\s*\\(?[a-zA-Z]\\)\\s+", "");
        t = t.replaceFirst("^\\s*\\(?[ivxIVX]+\\)\\s+", "");
        return t.trim();
    }

    private List<String> dedupeStrings(List<String> in) {
        if (in == null || in.isEmpty()) return List.of();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : in) {
            if (s == null) continue;
            String t = s.trim();
            if (!t.isBlank()) set.add(t);
        }
        return new ArrayList<>(set);
    }

    private List<String> extractKeywordsLight(String text) {
        if (text == null || text.isBlank()) return List.of();
        String lower = text.toLowerCase(Locale.ROOT);
        String[] parts = lower.replaceAll("[^\\p{L}\\p{N}\\s/\\-]", " ").split("\\s+");

        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String w = p == null ? "" : p.trim();
            if (w.length() < 3) continue;
            if (Set.of("và","hoặc","của","các","một","những","được","the","and","or","to","of","for","with").contains(w)) {
                continue;
            }
            out.add(w);
            if (out.size() >= 6) break;
        }
        return out;
    }

    // =========================================================
    // Generic: split big file into sections by headings
    // =========================================================
    private HeuristicDetect detectSectionsByHeadings(String extracted) {
        if (extracted == null || extracted.isBlank()) return null;

        String[] lines = extracted.split("\\r?\\n");
        List<Integer> headingIdx = new ArrayList<>();

        for (int i = 0; i < Math.min(lines.length, MAX_HEADING_LINES_SCAN); i++) {
            String raw = lines[i] == null ? "" : lines[i].trim();
            if (raw.isBlank()) continue;

            if (looksLikeHeading(raw)) {
                headingIdx.add(i);
                if (headingIdx.size() >= MAX_SECTIONS) break;
            }
        }

        if (headingIdx.size() < 2) return null;

        List<TopicOptionResponse> options = new ArrayList<>();
        Map<String, String> focusById = new LinkedHashMap<>();

        for (int k = 0; k < headingIdx.size(); k++) {
            int start = headingIdx.get(k);
            int end = (k + 1 < headingIdx.size())
                    ? headingIdx.get(k + 1)
                    : Math.min(lines.length, start + MAX_SECTION_LINES);

            String title = safeTrim(lines[start] == null ? "" : lines[start].trim(), 180);
            if (title.isBlank()) title = "Phần " + (k + 1);

            String focus = buildFocusFromLines(lines, start, end);

            String id = "SEC_" + (k + 1);

            TopicOptionResponse opt = new TopicOptionResponse();
            opt.setId(id);
            opt.setTitle(title);
            opt.setSummary("Chọn phần này để tạo đề tập trung đúng nội dung.");
            opt.setKeywords(extractKeywordsLight(title));
            options.add(opt);

            focusById.put(id, focus);
        }

        options = dedupeById(options);
        if (options.size() < 2) return null;

        HeuristicDetect out = new HeuristicDetect();
        out.options = options;
        out.focusById = focusById;
        return out;
    }

    private boolean looksLikeHeading(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isBlank()) return false;

        // markdown headings
        if (s.startsWith("#")) return true;

        String lower = s.toLowerCase(Locale.ROOT);

        // common VN/EN headings
        if (lower.startsWith("chương ") || lower.startsWith("bài ")
                || lower.startsWith("chapter ") || lower.startsWith("lesson ")
                || lower.startsWith("phần ") || lower.startsWith("mục ")
                || lower.startsWith("section ") || lower.startsWith("unit ")) {
            return true;
        }

        // numbering headings
        if (s.matches("^(\\d+\\.)\\s+.+$")) return true;          // 1. ...
        if (s.matches("^(\\d+\\.\\d+)\\s*.+$")) return true;      // 1.1 ...
        if (s.matches("^([IVX]+\\.)\\s+.+$")) return true;        // I. ...

        // very short all-caps line is often a heading
        if (s.length() <= 60 && s.equals(s.toUpperCase(Locale.ROOT)) && s.matches(".*[A-Z].*")) return true;

        return false;
    }

    private String buildFocusFromLines(String[] lines, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            String s = lines[i] == null ? "" : lines[i].trim();
            if (s.isBlank()) continue;
            sb.append(s).append("\n");
            if (sb.length() >= MAX_FOCUS_TEXT_CHARS) break;
        }
        return safeTrim(sb.toString(), MAX_FOCUS_TEXT_CHARS);
    }

    // =========================================================
    // Multi-lesson rough indicator (used only for fallback)
    // =========================================================
    private boolean looksLikeMultiLesson(String text) {
        if (text == null) return false;
        String t = text;
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

    // =========================================================
    // AI detect prompt + contract
    // =========================================================
    private String buildDetectPrompt(String trimmedMaterial) {
        return """
Bạn là trợ giảng. Hãy đọc học liệu dưới đây và xác định xem nội dung có thể chia thành NHIỀU PHẦN NHỎ / BÀI NHỎ để làm bài riêng hay không.

QUY TẮC:
- Nếu học liệu chứa nhiều phần (chương/bài/tiểu mục) khác nhau => isMulti=true và liệt kê 2..8 phần để học viên chọn.
- Nếu học liệu chỉ tập trung vào 1 nội dung => isMulti=false và topics=[].
- focusText PHẢI là đoạn trích từ học liệu, dùng để tạo đề tập trung cho phần đó.

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

    private List<TopicOptionResponse> dedupeById(List<TopicOptionResponse> in) {
        if (in == null || in.isEmpty()) return List.of();
        Map<String, TopicOptionResponse> map = new LinkedHashMap<>();
        for (TopicOptionResponse o : in) {
            if (o == null || o.getId() == null) continue;
            map.putIfAbsent(o.getId(), o);
        }
        return new ArrayList<>(map.values());
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