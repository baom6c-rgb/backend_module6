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

    // =========================================================
    // Goal (behavior):
    // 1) File lớn (PDF/Doc): AI lọc + tổng hợp + chia nhỏ thành các phần/bài để chọn (ưu tiên AI).
    // 2) File nhỏ chỉ 1 nội dung: không show chọn topic => generate luôn.
    // 3) Chỉ có "Mục tiêu" (objectives) => tách từng mục tiêu để chọn.
    // 4) Chỉ có outline/headings (memo/ref/context...) => tách từng đầu mục để chọn.
    // =========================================================

    // ============ Size guards ============
    private static final int SMALL_TEXT_ASSUME_SINGLE_CHARS = 1400;

    // How much text we send AI for "detect topics"
    private static final int MAX_DETECT_CHARS = 9000;

    // focus text for a single topic
    private static final int MAX_FOCUS_TEXT_CHARS = 2600;

    // focus text when merging multiple selected topics
    private static final int MAX_MULTI_FOCUS_TEXT_CHARS = 6500;

    // extremely large materials => must split (avoid generating from one blob)
    private static final int FORCE_MULTI_IF_EXCEEDS_CHARS = 200_000;

    // ============ Outline-only ============
    private static final int OUTLINE_SCAN_LIMIT = 7000;
    private static final int OUTLINE_MAX_LINE_LEN = 160;
    private static final int OUTLINE_MIN_ITEMS = 3;
    private static final int OUTLINE_MAX_ITEMS = 40;

    // ============ Objective bullets ============
    private static final int BULLET_SECTION_SCAN_LIMIT = 9000;
    private static final int MIN_OBJECTIVE_BULLETS_TO_TRIGGER = 4;
    private static final int MAX_OBJECTIVE_OPTIONS = 40;
    private static final int MAX_BULLET_LINE_LEN = 320;

    // ============ Headings/sections heuristic fallback ============
    private static final int MAX_HEADING_LINES_SCAN = 1000;
    private static final int MAX_SECTIONS = 12;
    private static final int MAX_SECTION_LINES = 220;

    // Bullet line detector: • - * 1. (1) a) I.
    private static final Pattern BULLET_LINE = Pattern.compile(
            "^\\s*(?:[•\\-*–—]|\\d+\\.|\\(?[a-zA-Z]\\)|\\(?[ivxIVX]+\\)|\\([0-9]+\\))\\s+.+$"
    );

    // Heading-only lines like "Mục tiêu:" / "Objectives:"
    private static final Pattern HEADING_ONLY_LINE = Pattern.compile(
            "^\\s*(?:mục tiêu|objectives?|learning outcomes?|outcomes?|goals?|yêu cầu)\\s*:?\\s*$",
            Pattern.CASE_INSENSITIVE
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

        // 0) Nếu chỉ là outline (đầu mục) => tách từng item để chọn (NO AI)
        HeuristicDetect outline = detectOutlineOnlyTopics(extracted);
        if (outline != null && outline.options.size() >= 2) {
            return cacheAndReturnMulti(currentEmail, materialId, outline);
        }

        // 1) Nếu chỉ là mục tiêu (objective-only) => tách từng mục tiêu để chọn (NO AI)
        HeuristicDetect objectivesOnly = detectObjectivesOnly(extracted);
        if (objectivesOnly != null && objectivesOnly.options.size() >= 2) {
            return cacheAndReturnMulti(currentEmail, materialId, objectivesOnly);
        }

        // 2) File nhỏ + 1 nội dung => generate luôn (không show chọn)
        if (isSmallSingleContent(extracted)) {
            TopicSelectionResult r = new TopicSelectionResult();
            r.isMulti = false;
            r.topics = List.of();
            r.selectionToken = null;
            return r;
        }

        // 3) File cực lớn => bắt buộc chia (ưu tiên AI split)
        if (extracted.length() >= FORCE_MULTI_IF_EXCEEDS_CHARS) {
            HeuristicDetect aiHd = detectByAiHeuristic(extracted);
            if (aiHd != null && aiHd.options.size() >= 2) {
                return cacheAndReturnMulti(currentEmail, materialId, aiHd);
            }

            // fallback: headings split
            HeuristicDetect sec = detectSectionsByHeadings(extracted);
            if (sec != null && sec.options.size() >= 2) {
                return cacheAndReturnMulti(currentEmail, materialId, sec);
            }

            // nếu vẫn fail: coi như single (nhưng thực tế ít khi)
            TopicSelectionResult r = new TopicSelectionResult();
            r.isMulti = false;
            r.topics = List.of();
            r.selectionToken = null;
            return r;
        }

        // 4) NORMAL big/medium file => ưu tiên AI lọc + tổng hợp + chia bài
        HeuristicDetect aiHd = detectByAiHeuristic(extracted);
        if (aiHd != null && aiHd.options.size() >= 2) {
            return cacheAndReturnMulti(currentEmail, materialId, aiHd);
        }

        // 5) fallback: headings/sections heuristic (khi AI fail)
        HeuristicDetect sec = detectSectionsByHeadings(extracted);
        if (sec != null && sec.options.size() >= 2) {
            return cacheAndReturnMulti(currentEmail, materialId, sec);
        }

        // 6) fallback: nếu vẫn không ra => single
        TopicSelectionResult r = new TopicSelectionResult();
        r.isMulti = false;
        r.topics = List.of();
        r.selectionToken = null;
        return r;
    }

    // =========================================================
    // Resolve focus text (single or multiple)
    // =========================================================

    @Override
    public String resolveFocusText(String currentEmail, String selectionToken, String topicId) {
        if (selectionToken == null || selectionToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "selectionToken is required");
        }
        if (topicId == null || topicId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "topicId is required");
        }

        TopicSelectionCacheData data = getCacheDataOrThrow(currentEmail, selectionToken);

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

        TopicSelectionCacheData data = getCacheDataOrThrow(currentEmail, selectionToken);

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
            merged = merged.substring(0, MAX_MULTI_FOCUS_TEXT_CHARS).trim();
        }

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

    // =========================================================
    // Cache helpers
    // =========================================================

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

    private TopicSelectionCacheData getCacheDataOrThrow(String currentEmail, String selectionToken) {
        Object obj = practiceSessionCache.getIfPresent(selectionKey(currentEmail, selectionToken));
        if (!(obj instanceof TopicSelectionCacheData data)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Topic selection expired. Please generate again.");
        }
        if (!Objects.equals(data.userEmail, currentEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Selection does not belong to current user");
        }
        return data;
    }

    private String selectionKey(String email, String token) {
        return "topicSel:" + email + ":" + token;
    }

    // =========================================================
    // Decision: small single content
    // - Nếu ít chữ và không có dấu hiệu multi => generate luôn
    // - Nếu chỉ là mục tiêu/outline thì đã return ở trên
    // =========================================================
    private boolean isSmallSingleContent(String extracted) {
        if (extracted == null) return true;
        String t = extracted.trim();
        if (t.length() > SMALL_TEXT_ASSUME_SINGLE_CHARS) return false;

        // nếu có nhiều heading markers => không coi là single
        if (looksLikeMultiLesson(t)) return false;

        // nếu có quá nhiều bullet => thường là objectives/outline, nhưng đã lọc trước
        int bulletLines = countBulletLines(t, 160);
        return bulletLines < 4;
    }

    private int countBulletLines(String text, int maxLines) {
        String[] lines = (text == null ? "" : text).split("\\r?\\n");
        int scan = Math.min(lines.length, maxLines);
        int count = 0;
        for (int i = 0; i < scan; i++) {
            String raw = lines[i] == null ? "" : lines[i].trim();
            if (raw.isBlank()) continue;
            if (BULLET_LINE.matcher(raw).matches()) count++;
        }
        return count;
    }

    // =========================================================
    // RULE: Outline-only detector (NO AI)
    // =========================================================
    private HeuristicDetect detectOutlineOnlyTopics(String extracted) {
        String scan = extracted.length() > OUTLINE_SCAN_LIMIT
                ? extracted.substring(0, OUTLINE_SCAN_LIMIT)
                : extracted;

        String[] lines = scan.split("\\r?\\n");

        List<String> items = new ArrayList<>();
        int nonEmptyLines = 0;
        int shortListLikeLines = 0;
        int longParagraphLines = 0;

        for (int i = 0; i < Math.min(lines.length, 320); i++) {
            String raw = lines[i] == null ? "" : lines[i].trim();
            if (raw.isBlank()) continue;

            nonEmptyLines++;
            if (raw.length() > 240) longParagraphLines++;

            boolean isBullet = BULLET_LINE.matcher(raw).matches();
            boolean isShort = raw.length() <= OUTLINE_MAX_LINE_LEN;

            if (isShort && (isBullet || looksLikeListLine(raw) || looksLikeHeading(raw))) {
                shortListLikeLines++;
            }

            if (isBullet) {
                String cleaned = stripBulletPrefix(raw).replaceAll("\\s+", " ").trim();
                if (isGoodOutlineItem(cleaned)) items.add(cleaned);
                continue;
            }

            if (looksLikeListLine(raw)) {
                items.addAll(splitInlineItems(raw));
                continue;
            }

            // short standalone heading can be an item (but not "Mục tiêu")
            if (isShort && looksLikeHeading(raw) && !HEADING_ONLY_LINE.matcher(raw).matches()) {
                if (isGoodOutlineItem(raw)) items.add(raw);
            }
        }

        items = dedupeStrings(items);

        boolean outlineLike = items.size() >= OUTLINE_MIN_ITEMS
                && shortListLikeLines >= Math.max(3, nonEmptyLines / 3)
                && longParagraphLines <= Math.max(2, nonEmptyLines / 10);

        if (!outlineLike) return null;

        List<TopicOptionResponse> options = new ArrayList<>();
        Map<String, String> focusById = new LinkedHashMap<>();

        int n = 1;
        for (String it : items) {
            if (options.size() >= OUTLINE_MAX_ITEMS) break;

            String title = cleanOutlineItemTitle(it);
            if (title.isBlank()) continue;

            String id = "OUT_" + (n++);

            TopicOptionResponse opt = new TopicOptionResponse();
            opt.setId(id);
            opt.setTitle(safeTrim(title, 180));
            opt.setSummary("Chọn mục này để hệ thống tạo đề ôn tập đúng trọng tâm bạn muốn.");
            opt.setKeywords(extractKeywordsLight(title));
            options.add(opt);

            focusById.put(id, safeTrim("CHỦ ĐỀ ÔN TẬP (chỉ tập trung chủ đề này): " + title, MAX_FOCUS_TEXT_CHARS));
        }

        options = normalizeOptions(options);
        if (options.size() < 2) return null;

        HeuristicDetect out = new HeuristicDetect();
        out.options = options;
        out.focusById = keepFocusForOptions(focusById, options);
        return out;
    }

    private boolean looksLikeListLine(String raw) {
        if (raw == null) return false;
        String s = raw.trim();
        if (s.isBlank()) return false;
        if (s.length() > 260) return false;

        int comma = countChar(s, ',');
        int semi = countChar(s, ';');
        boolean hasDash = s.contains(" - ") || s.contains(" – ") || s.contains(" — ");

        boolean manySeparators = (comma >= 2) || (semi >= 2);
        return manySeparators || (comma >= 1 && hasDash) || (comma >= 1 && s.length() <= 160);
    }

    private int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    private List<String> splitInlineItems(String raw) {
        if (raw == null) return List.of();
        String s = raw.trim();
        if (s.isBlank()) return List.of();

        s = s.replace("–", "-").replace("—", "-");

        String[] parts = s.split("\\s*,\\s*");
        List<String> out = new ArrayList<>();

        for (String p : parts) {
            if (p == null) continue;
            String t = p.trim();
            if (t.isBlank()) continue;

            if (t.contains(" - ")) {
                String[] pp = t.split("\\s-\\s");
                for (String x : pp) {
                    String xx = x == null ? "" : x.trim();
                    if (isGoodOutlineItem(xx)) out.add(xx);
                }
            } else {
                if (isGoodOutlineItem(t)) out.add(t);
            }
        }

        return out;
    }

    private boolean isGoodOutlineItem(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.isBlank()) return false;
        if (t.length() < 3) return false;
        if (t.length() > 240) return false;
        if (HEADING_ONLY_LINE.matcher(t).matches()) return false; // ignore "Mục tiêu"
        return true;
    }

    private String cleanOutlineItemTitle(String s) {
        if (s == null) return "";
        String t = s.trim();
        t = t.replaceAll("\\s+", " ");
        t = t.replaceAll("^[\\-–—]+\\s*", "");
        t = t.replaceAll("[;:]+$", "");
        return t.trim();
    }

    // =========================================================
    // RULE: Objectives-only detector (NO AI)
    // Quan trọng: chỉ trigger khi "chủ yếu là mục tiêu"
    // (để tránh case PDF lớn có 1 slide mục tiêu => bị tách theo mục tiêu như bạn đang gặp)
    // =========================================================
    private HeuristicDetect detectObjectivesOnly(String extracted) {
        String scan = extracted.length() > BULLET_SECTION_SCAN_LIMIT
                ? extracted.substring(0, BULLET_SECTION_SCAN_LIMIT)
                : extracted;

        String[] lines = scan.split("\\r?\\n");

        int headingLine = findObjectiveHeadingLine(lines);
        if (headingLine < 0) return null;

        List<String> bullets = collectBulletsAfterHeading(lines, headingLine);
        bullets = normalizeObjectiveBullets(bullets);
        bullets = dedupeStrings(bullets);

        if (bullets.size() < MIN_OBJECTIVE_BULLETS_TO_TRIGGER) return null;

        // --- objective-only gate ---
        // Nếu sau cụm mục tiêu còn nhiều content (nhiều dòng dài / nhiều heading khác),
        // thì coi như "NORMAL file" => KHÔNG trigger objectives-only.
        if (!isObjectivesDominant(lines, headingLine, bullets.size())) {
            return null;
        }

        List<TopicOptionResponse> options = new ArrayList<>();
        Map<String, String> focusById = new LinkedHashMap<>();

        int i = 1;
        for (String b : bullets) {
            if (options.size() >= MAX_OBJECTIVE_OPTIONS) break;

            String cleaned = cleanObjectiveSentence(b);
            if (cleaned.isBlank()) continue;

            String id = "OBJ_" + i++;

            TopicOptionResponse opt = new TopicOptionResponse();
            opt.setId(id);
            opt.setTitle(safeTrim(cleaned, 200));
            opt.setSummary("Chọn mục tiêu này để tạo đề ôn tập đúng trọng tâm mục tiêu bạn muốn luyện.");
            opt.setKeywords(extractKeywordsLight(cleaned));
            options.add(opt);

            focusById.put(id, safeTrim("MỤC TIÊU ÔN TẬP (chỉ tập trung mục tiêu này): " + cleaned, MAX_FOCUS_TEXT_CHARS));
        }

        options = normalizeOptions(options);
        if (options.size() < 2) return null;

        HeuristicDetect out = new HeuristicDetect();
        out.options = options;
        out.focusById = keepFocusForOptions(focusById, options);
        return out;
    }

    private boolean isObjectivesDominant(String[] lines, int headingLine, int bulletCount) {
        if (lines == null || lines.length == 0) return false;

        // Scan a window after objectives bullets
        int scanStart = Math.min(lines.length, headingLine + 1);
        int scanEnd = Math.min(lines.length, headingLine + 260);

        int nonEmpty = 0;
        int bulletLines = 0;
        int longLines = 0;
        int otherHeadings = 0;

        for (int i = scanStart; i < scanEnd; i++) {
            String raw = lines[i] == null ? "" : lines[i].trim();
            if (raw.isBlank()) continue;
            nonEmpty++;

            if (BULLET_LINE.matcher(raw).matches()) bulletLines++;
            if (raw.length() > 180) longLines++;
            if (looksLikeHeading(raw) && !HEADING_ONLY_LINE.matcher(raw).matches()) otherHeadings++;
        }

        // Heuristic:
        // - objective-only nếu bullet chiếm đa số và ít headings/content khác
        // - bulletCount đủ lớn và "otherHeadings" gần như không có
        if (nonEmpty == 0) return true;

        double bulletRatio = bulletLines / (double) nonEmpty;

        boolean mostlyBullets = bulletRatio >= 0.55;
        boolean fewOtherHeadings = otherHeadings <= 1;
        boolean fewLongContent = longLines <= Math.max(2, nonEmpty / 12);

        // bulletCount param ensures it's meaningful
        return bulletCount >= MIN_OBJECTIVE_BULLETS_TO_TRIGGER && mostlyBullets && fewOtherHeadings && fewLongContent;
    }

    private int findObjectiveHeadingLine(String[] lines) {
        if (lines == null || lines.length == 0) return -1;

        List<String> keys = List.of(
                "mục tiêu", "muc tieu", "mụctiêu",
                "mục đích", "muc dich",
                "kết quả", "ket qua",
                "đầu ra", "dau ra",
                "chuẩn đầu ra", "chuan dau ra",
                "objectives", "objective",
                "learning outcomes", "outcomes",
                "goals", "goal",
                "yêu cầu", "yeu cau"
        );

        for (int i = 0; i < Math.min(lines.length, 180); i++) {
            String raw = lines[i] == null ? "" : lines[i].trim();
            if (raw.isBlank()) continue;

            if (raw.length() > 160) continue;
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

        for (int i = headingLine + 1; i < Math.min(lines.length, headingLine + 240); i++) {
            String raw = lines[i] == null ? "" : lines[i].trim();

            if (raw.isBlank()) {
                blankCount++;
                if (blankCount >= 2 && out.size() >= 2) break;
                continue;
            }
            blankCount = 0;

            if (!BULLET_LINE.matcher(raw).matches()) {
                // stop if next heading begins
                if (looksLikeHeading(raw)) break;
                continue;
            }

            if (raw.length() > MAX_BULLET_LINE_LEN) continue;

            String cleaned = stripBulletPrefix(raw).replaceAll("\\s+", " ").trim();
            if (cleaned.length() < 6) continue;

            out.add(cleaned);
        }

        return out;
    }

    private List<String> normalizeObjectiveBullets(List<String> bullets) {
        if (bullets == null || bullets.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (String b : bullets) {
            if (b == null) continue;
            String t = b.trim().replaceAll("\\s+", " ");
            if (!t.isBlank()) out.add(t);
        }
        return out;
    }

    private String cleanObjectiveSentence(String s) {
        if (s == null) return "";
        String t = s.trim().replaceAll("\\s+", " ");
        t = t.replaceAll("[;:]+$", "").trim();
        return t;
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

    // =========================================================
    // PRIMARY: AI detect topics (lọc + tổng hợp + chia bài)
    // =========================================================
    private HeuristicDetect detectByAiHeuristic(String extracted) {
        String trimmed = extracted.length() > MAX_DETECT_CHARS
                ? extracted.substring(0, MAX_DETECT_CHARS)
                : extracted;

        String prompt = buildDetectPrompt(trimmed);
        String contract = detectJsonContract();

        String json;
        try {
            json = ai.generateJson(prompt, contract);
        } catch (Exception e) {
            return null;
        }

        DetectResult dr;
        try {
            dr = om.readValue(json, DetectResult.class);
        } catch (Exception e) {
            return null;
        }

        List<DetectTopic> topics = (dr == null || dr.topics == null) ? List.of() : dr.topics;
        boolean multi = Boolean.TRUE.equals(dr.isMulti) && topics.size() >= 2;
        if (!multi) return null;

        List<TopicOptionResponse> options = new ArrayList<>();
        Map<String, String> focusById = new LinkedHashMap<>();

        int idx = 1;
        for (DetectTopic t : topics) {
            if (t == null) continue;

            String id = safeId(t.id);
            if (id.isBlank()) id = "T" + (idx++);

            String title = safeTrim(t.title, 160);
            String summary = safeTrim(t.summary, 320);
            if (title.isBlank()) continue;

            TopicOptionResponse opt = new TopicOptionResponse();
            opt.setId(id);
            opt.setTitle(title);
            opt.setSummary(summary);
            opt.setKeywords(t.keywords == null ? List.of() : trimList(t.keywords, 8, 40));
            options.add(opt);

            // focusText: AI đã "lọc/tổng hợp" từ học liệu, nhưng vẫn phải không bịa.
            String focus = safeTrim(t.focusText, MAX_FOCUS_TEXT_CHARS);

            // fallback nếu AI trả focusText rỗng
            if (focus.isBlank()) {
                focus = findExcerptBySignals(trimmed, title, opt.getKeywords());
                if (focus.isBlank()) focus = (title + "\n" + summary).trim();
            }

            focusById.put(id, focus);
        }

        options = normalizeOptions(options);
        if (options.size() < 2) return null;

        HeuristicDetect out = new HeuristicDetect();
        out.options = options;
        out.focusById = keepFocusForOptions(focusById, options);
        return out;
    }

    private String buildDetectPrompt(String trimmedMaterial) {
        return """
Bạn là trợ giảng. Nhiệm vụ: ĐỌC HỌC LIỆU và "LỌC + TỔNG HỢP" để chia thành các PHẦN/BÀI ÔN TẬP nhỏ, rõ ràng, giúp học viên nhìn vào là chọn được.

QUAN TRỌNG:
- ƯU TIÊN chia theo các ý lớn/nhóm kiến thức có trong file (ví dụ: khái niệm, cấu trúc, annotation, ví dụ, workflow, lưu ý, lỗi thường gặp...).
- Nếu file là slide nhiều trang: hãy gom theo các "cụm nội dung" (không chỉ copy đúng tiêu đề 1 slide).
- Không chia theo "Mục tiêu" nếu file còn nhiều nội dung sau mục tiêu. Mục tiêu chỉ là tham khảo, phần chính vẫn là nội dung bài.

ĐẦU RA:
- Nếu học liệu có nhiều phần/bài => isMulti=true và topics gồm 2..12 phần.
- Nếu học liệu chỉ có 1 nội dung duy nhất => isMulti=false và topics=[].

MỖI TOPIC:
- title: tên phần/bài ngắn gọn (5-12 từ), mô tả đúng nội dung.
- summary: 1-2 câu mô tả phần này học gì/ôn gì.
- keywords: 3-8 từ khóa quan trọng.
- focusText: đoạn "nội dung đã lọc & tổng hợp" TRÍCH/CHẮP NỐI từ học liệu.
  + Được phép rút gọn và ghép nhiều mẩu ngắn, nhưng KHÔNG được thêm kiến thức ngoài học liệu.
  + Không bịa ví dụ mới, không thêm định nghĩa không có trong file.

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
      "title": "....",
      "summary": "....",
      "keywords": ["..."],
      "focusText": "...."
    }
  ]
}

RULES:
- isMulti ∈ {true,false}
- Nếu isMulti=false => topics phải là []
- Nếu isMulti=true => topics có 2..12 phần tử
- title: <= 160 ký tự
- summary: <= 320 ký tự
- keywords: 3..8 phần tử, mỗi keyword <= 40 ký tự
- focusText: <= 2600 ký tự; chỉ lấy từ học liệu (có thể rút gọn/ghép đoạn ngắn), KHÔNG thêm kiến thức ngoài học liệu
- KẾT THÚC OUTPUT bằng dấu }
""";
    }

    // =========================================================
    // Fallback: split by headings/sections
    // =========================================================
    private HeuristicDetect detectSectionsByHeadings(String extracted) {
        if (extracted == null || extracted.isBlank()) return null;

        String[] lines = extracted.split("\\r?\\n");
        List<Integer> headingIdx = new ArrayList<>();

        for (int i = 0; i < Math.min(lines.length, MAX_HEADING_LINES_SCAN); i++) {
            String raw = lines[i] == null ? "" : lines[i].trim();
            if (raw.isBlank()) continue;

            // ignore pure "Mục tiêu" heading
            if (HEADING_ONLY_LINE.matcher(raw).matches()) continue;

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

        options = normalizeOptions(options);
        if (options.size() < 2) return null;

        HeuristicDetect out = new HeuristicDetect();
        out.options = options;
        out.focusById = keepFocusForOptions(focusById, options);
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
        if (s.matches("^(\\d+\\.)\\s+[^\\d].+$")) return true;     // 1. Title
        if (s.matches("^(\\d+\\.\\d+)\\s*.+$")) return true;       // 1.1 Title
        if (s.matches("^([IVX]+\\.)\\s+.+$")) return true;         // I. Title

        // short ALL CAPS line
        if (s.length() <= 52 && s.equals(s.toUpperCase(Locale.ROOT)) && s.matches(".*[A-Z].*")) return true;

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
    // Multi-lesson rough indicator
    // =========================================================
    private boolean looksLikeMultiLesson(String text) {
        if (text == null) return false;

        String[] lines = text.split("\\r?\\n");
        int scan = Math.min(lines.length, 320);
        int hits = 0;

        for (int i = 0; i < scan; i++) {
            String s = lines[i] == null ? "" : lines[i].trim();
            if (s.isBlank()) continue;

            if (HEADING_ONLY_LINE.matcher(s).matches()) continue;

            String lower = s.toLowerCase(Locale.ROOT);

            if (lower.startsWith("chương ") || lower.startsWith("bài ")
                    || lower.startsWith("chapter ") || lower.startsWith("lesson ")
                    || lower.startsWith("phần ") || lower.startsWith("mục ")
                    || lower.startsWith("section ") || lower.startsWith("unit ")) {
                hits++;
            } else if (s.startsWith("#") || s.startsWith("##")) {
                hits++;
            } else if (s.matches("^([IVX]+\\.)\\s+.+$")) {
                hits++;
            } else if (s.matches("^(\\d+\\.)\\s+[^\\d].+$")) {
                hits++;
            }

            if (hits >= 3) return true;
        }
        return false;
    }

    // =========================================================
    // Excerpt fallback (when AI focusText empty)
    // =========================================================
    private String findExcerptBySignals(String material, String title, List<String> keywords) {
        if (material == null || material.isBlank()) return "";

        List<String> signals = new ArrayList<>();
        if (title != null && !title.isBlank()) signals.add(title.trim());
        if (keywords != null) {
            for (String k : keywords) {
                if (k != null && !k.isBlank()) signals.add(k.trim());
            }
        }

        String[] lines = material.split("\\r?\\n");
        int bestIdx = -1;

        for (int i = 0; i < Math.min(lines.length, 900); i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isBlank()) continue;

            String lower = line.toLowerCase(Locale.ROOT);
            for (String s : signals) {
                String sl = s.toLowerCase(Locale.ROOT);
                if (sl.length() < 3) continue;
                if (lower.contains(sl)) {
                    bestIdx = i;
                    break;
                }
            }
            if (bestIdx >= 0) break;
        }

        if (bestIdx < 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(lines.length, 32); i++) {
                String line = lines[i] == null ? "" : lines[i].trim();
                if (line.isBlank()) continue;
                sb.append(line).append("\n");
                if (sb.length() >= 1000) break;
            }
            return safeTrim(sb.toString(), MAX_FOCUS_TEXT_CHARS);
        }

        int start = Math.max(0, bestIdx - 3);
        int end = Math.min(lines.length, bestIdx + 26);

        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isBlank()) continue;
            sb.append(line).append("\n");
            if (sb.length() >= MAX_FOCUS_TEXT_CHARS) break;
        }

        return safeTrim(sb.toString(), MAX_FOCUS_TEXT_CHARS);
    }

    // =========================================================
    // Common helpers
    // =========================================================
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

    private List<String> extractKeywordsLight(String text) {
        if (text == null || text.isBlank()) return List.of();
        String lower = text.toLowerCase(Locale.ROOT);
        String[] parts = lower.replaceAll("[^\\p{L}\\p{N}\\s/\\-]", " ").split("\\s+");

        Set<String> stop = Set.of(
                "và", "hoặc", "của", "các", "một", "những", "được", "trong", "với", "cho", "khi",
                "the", "and", "or", "to", "of", "for", "with", "in", "on", "by", "from"
        );

        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String w = p == null ? "" : p.trim();
            if (w.length() < 3) continue;
            if (stop.contains(w)) continue;
            out.add(w);
            if (out.size() >= 8) break;
        }
        return out;
    }

    private List<TopicOptionResponse> normalizeOptions(List<TopicOptionResponse> in) {
        if (in == null || in.isEmpty()) return List.of();

        // drop empty titles + dedupe by id (keep first)
        Map<String, TopicOptionResponse> map = new LinkedHashMap<>();
        for (TopicOptionResponse o : in) {
            if (o == null) continue;
            String id = o.getId();
            String title = o.getTitle() == null ? "" : o.getTitle().trim();
            if (id == null || id.isBlank()) continue;
            if (title.isBlank()) continue;
            map.putIfAbsent(id, o);
        }
        return new ArrayList<>(map.values());
    }

    private Map<String, String> keepFocusForOptions(Map<String, String> focusById, List<TopicOptionResponse> options) {
        if (focusById == null) return new LinkedHashMap<>();
        Set<String> ids = new HashSet<>();
        for (TopicOptionResponse o : options) {
            if (o != null && o.getId() != null) ids.add(o.getId());
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (String id : focusById.keySet()) {
            if (ids.contains(id)) out.put(id, focusById.get(id));
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
        public Map<String, String> focusById = new LinkedHashMap<>();
    }
}