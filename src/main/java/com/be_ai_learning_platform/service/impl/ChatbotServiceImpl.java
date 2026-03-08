package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.AI.AiResponsesRouter;
import com.be_ai_learning_platform.dto.response.ChatAskResponse;
import com.be_ai_learning_platform.dto.response.ChatMessageResponse;
import com.be_ai_learning_platform.dto.response.ChatSessionResponse;
import com.be_ai_learning_platform.entity.ChatMessage;
import com.be_ai_learning_platform.entity.ChatSession;
import com.be_ai_learning_platform.entity.LearningMaterial;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.SenderType;
import com.be_ai_learning_platform.repository.ChatMessageRepository;
import com.be_ai_learning_platform.repository.ChatSessionRepository;
import com.be_ai_learning_platform.repository.LearningMaterialRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.ChatbotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatbotServiceImpl implements ChatbotService {

    private final AiResponsesRouter responsesClient;
    private final UserRepository userRepo;
    private final LearningMaterialRepository materialRepo;
    private final ChatSessionRepository sessionRepo;
    private final ChatMessageRepository messageRepo;

    // ===== US17 guardrails =====
    private static final int MAX_KEYWORDS_CHARS = 80;
    private static final int MAX_KEYWORDS_WORDS = 8;

    // ✅ relevance should be stricter (meaningful tokens)
    // NOTE: Keep as 1 to avoid false negatives on short materials.
    private static final int MIN_RELEVANT_HITS = 1;

    private static final int MAX_CONTEXT_CHARS = 1800;

    // ✅ make reply shorter & vaguer
    private static final int MAX_REPLY_CHARS = 520;
    private static final int MAX_BULLETS = 5;
    private static final int MIN_BULLETS = 3;

    /**
     * ✅ Stop words để tránh match linh tinh kiểu: "là", "ai", "gì", "the", ...
     * Bổ sung thêm nhóm từ chung chung hay làm relevance pass sai ("bằng", "mấy", "bao nhiêu"...)
     */
    private static final Set<String> STOP_WORDS = Set.of(
            "la", "là", "ai", "gi", "gì", "nao", "nào",
            "the", "a", "an", "and", "or",

            // ✅ VN generic (hay làm relevance bị pass sai)
            "bang", "bằng", "may", "mấy", "bao", "nhiêu", "baonhieu",
            "cai", "cái", "nay", "này", "do", "đó", "noi", "nói", "ve", "về",
            "cho", "giup", "giúp","mày","tao"
    );

    // ================= PUBLIC API =================

    @Override
    public ChatSessionResponse startSession(Long materialId) {
        User user = getCurrentUser();
        LearningMaterial material = materialRepo.findByIdAndUser(materialId, user)
                .orElseThrow(() -> notFound("Material not found"));

        ChatSession session = sessionRepo
                .findTopByUser_IdAndMaterial_IdOrderByCreatedAtDesc(user.getId(), materialId)
                .orElseGet(() -> {
                    ChatSession s = new ChatSession();
                    s.setUser(user);
                    s.setMaterial(material);
                    return sessionRepo.save(s);
                });

        ChatSessionResponse res = new ChatSessionResponse();
        res.setSessionId(session.getId());
        res.setMaterialId(materialId);
        res.setCreatedAt(session.getCreatedAt());
        return res;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(Long sessionId) {
        User user = getCurrentUser();
        ChatSession session = getSessionOrThrow(sessionId, user);

        return messageRepo.findByChatSession_IdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(this::toMessageResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ChatAskResponse ask(Long sessionId, String keywords) {
        User user = getCurrentUser();
        ChatSession session = getSessionOrThrow(sessionId, user);

        String validatedKeywords = validateKeywords(keywords);
        LearningMaterial material = session.getMaterial();

        // ✅ luôn lưu message của user để UI render lịch sử ổn định
        saveMessage(session, SenderType.USER, validatedKeywords);

        // ✅ hard-block rõ ràng ngoài phạm vi (toán cơ bản/định danh/người nổi tiếng...)
        if (isClearlyOutOfScope(validatedKeywords)) {
            String msg = notInMaterialAnswer(validatedKeywords);
            saveMessage(session, SenderType.AI, msg);

            ChatAskResponse res = new ChatAskResponse();
            res.setSessionId(sessionId);
            res.setAnswer(msg);
            return res;
        }

        // ✅ relevance check: dùng meaningful tokens để tránh pass sai
        int hits = countKeywordHits(material.getExtractedText(), validatedKeywords);
        if (hits < MIN_RELEVANT_HITS) {
            String msg = notInMaterialAnswer(validatedKeywords);
            saveMessage(session, SenderType.AI, msg);

            ChatAskResponse res = new ChatAskResponse();
            res.setSessionId(sessionId);
            res.setAnswer(msg);
            return res;
        }

        // ✅ build context cũng phải meaningful, tránh match "AI" / "là" / "ai" linh tinh
        String context = buildContext(material.getExtractedText(), validatedKeywords);

        // ✅ nếu context rỗng => coi như out-of-scope
        if (context == null || context.isBlank()) {
            String msg = notInMaterialAnswer(validatedKeywords);
            saveMessage(session, SenderType.AI, msg);

            ChatAskResponse res = new ChatAskResponse();
            res.setSessionId(sessionId);
            res.setAnswer(msg);
            return res;
        }

        String prompt = buildPrompt(validatedKeywords, context);

        String aiAnswer = responsesClient.generateText(prompt);
        aiAnswer = postFilter(aiAnswer, validatedKeywords);

        // save AI message
        saveMessage(session, SenderType.AI, aiAnswer);

        ChatAskResponse res = new ChatAskResponse();
        res.setSessionId(sessionId);
        res.setAnswer(aiAnswer);
        return res;
    }

    // ================= INTERNAL HELPERS =================

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw unauthorized("Unauthenticated");
        }
        return userRepo.findByEmail(auth.getName())
                .orElseThrow(() -> unauthorized("User not found"));
    }

    private ChatSession getSessionOrThrow(Long sessionId, User user) {
        ChatSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> notFound("Chat session not found"));
        if (!session.getUser().getId().equals(user.getId())) {
            throw forbidden("Not your chat session");
        }
        return session;
    }

    private void saveMessage(ChatSession session, SenderType sender, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setChatSession(session);
        msg.setSender(sender);
        msg.setContent(content);
        messageRepo.save(msg);
    }

    /**
     * ✅ Khi user hỏi ngoài phạm vi tài liệu hiện tại
     */
    private String notInMaterialAnswer(String keywords) {
        return "Nội dung bạn vừa hỏi không tồn tại trong tài liệu/bài quiz hiện tại, nên mình không thể hỗ trợ phần này.";
    }

    /**
     * ✅ Hard block các input rõ ràng ngoài phạm vi tài liệu (toán cơ bản/định danh/người nổi tiếng...)
     * Mục tiêu: không để các từ khóa generic như "bằng", "mấy" làm pass relevance.
     */
    private boolean isClearlyOutOfScope(String keywords) {
        String s = normalizeSpaces(keywords).toLowerCase(Locale.ROOT);

        // detect arithmetic like "1+1", "2 * 3", "10/2"
        boolean looksMath = s.matches(".*\\d+\\s*[+\\-*/]\\s*\\d+.*");

        // common identity / celebrity bait (có thể mở rộng sau)
        boolean looksIdentity = s.contains("tao la ai") || s.contains("tôi là ai") || s.contains("toi la ai");
        boolean looksCelebrity = s.contains("son tung") || s.contains("sơn tùng");

        return looksMath || looksIdentity || looksCelebrity;
    }

    /**
     * US17: keyword-only validation
     */
    private String validateKeywords(String raw) {
        String s = safe(raw).trim();
        if (s.isBlank()) throw badRequest("Vui lòng nhập từ khóa");

        if (s.length() > MAX_KEYWORDS_CHARS)
            throw badRequest("Từ khóa quá dài");

        if (s.contains("\n") || s.contains("\r") || s.contains("\t"))
            throw badRequest("Không được dán nguyên câu hỏi");

        if (s.matches(".*[?.!:;].*"))
            throw badRequest("Chỉ nhập từ khóa, không nhập dạng câu hỏi");

        if (!s.matches("^[\\p{L}\\p{N}\\s,_\\-+/]+$"))
            throw badRequest("Từ khóa chứa ký tự không hợp lệ");

        String[] words = s.split("\\s+");
        if (words.length > MAX_KEYWORDS_WORDS)
            throw badRequest("Quá nhiều từ khóa");

        return String.join(" ", words);
    }
    private List<String> extractMeaningfulTokens(String keywords) {
        String norm = normalizeSpaces(keywords);
        if (norm.isBlank()) return List.of();

        return Arrays.stream(norm.split("\\s+"))
                .map(this::normalize)           // lowercase + NFKC
                .map(String::trim)
                .filter(t -> !t.isBlank())
                .filter(t -> t.length() >= 3)
                // ✅ token phải có chữ cái
                .filter(t -> t.matches(".*\\p{L}+.*"))
                .filter(t -> !STOP_WORDS.contains(t))
                .distinct()
                .collect(Collectors.toList());
    }

    private int countKeywordHits(String text, String keywords) {
        if (text == null || text.isBlank()) return 0;

        String hay = normalize(text);
        List<String> tokens = extractMeaningfulTokens(keywords);

        // nếu toàn stopwords / không meaningful => coi như out-of-scope luôn
        if (tokens.isEmpty()) return 0;

        int hits = 0;
        for (String t : tokens) {
            if (hay.contains(t)) hits++;
        }
        return hits;
    }

    private String buildContext(String extractedText, String keywords) {
        if (extractedText == null || extractedText.isBlank()) return "";

        List<String> tokens = extractMeaningfulTokens(keywords);
        if (tokens.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (String para : extractedText.split("\\n\\s*\\n")) {
            String pNorm = normalize(para);
            if (tokens.stream().anyMatch(pNorm::contains)) {
                sb.append(para).append("\n---\n");
                if (sb.length() > MAX_CONTEXT_CHARS) break;
            }
        }
        return truncate(sb.toString(), MAX_CONTEXT_CHARS);
    }

    /**
     * 🔒 STRICT IN-SCOPE PROMPT
     * - AI CHỈ được trả lời về khái niệm xuất hiện TRỰC TIẾP trong NGỮ CẢNH
     * - Bất kỳ từ khóa nào KHÔNG có trong ngữ cảnh → PHẢI TỪ CHỐI
     * - Không suy luận, không kiến thức phổ thông
     */
    private String buildPrompt(String keywords, String context) {
        return """
                Bạn là trợ giảng hỗ trợ học viên TRONG PHẠM VI TÀI LIỆU HIỆN TẠI.

                NGUYÊN TẮC TUYỆT ĐỐI (PHẢI TUÂN THỦ):
                - CHỈ sử dụng thông tin CÓ TRONG NGỮ CẢNH bên dưới.
                - KHÔNG dùng kiến thức phổ thông, kiến thức đời sống, hay suy luận ngoài tài liệu.
                - Nếu TỪ KHÓA KHÔNG XUẤT HIỆN RÕ RÀNG trong NGỮ CẢNH → PHẢI TỪ CHỐI TRẢ LỜI.
                - KHÔNG trả lời các câu hỏi như: toán học cơ bản, người nổi tiếng, định danh cá nhân,
                  kiến thức ngoài bài học (ví dụ: "1+1 bằng mấy", "tao là ai", "sơn tùng là ai", ...).

                ĐỊNH DẠNG BẮT BUỘC KHI TRẢ LỜI:
                - Chỉ đưa GỢI Ý MƠ HỒ, không giải thích chi tiết.
                - 3 đến 5 gạch đầu dòng.
                - Mỗi gạch đầu dòng tối đa 12 từ.
                - Mỗi dòng bắt đầu bằng "- ".
                - KHÔNG đáp án, KHÔNG chọn A/B/C/D, KHÔNG hướng dẫn từng bước.

                TRƯỜNG HỢP NGOÀI PHẠM VI:
                - Nếu từ khóa không nằm trong ngữ cảnh → trả lời DUY NHẤT câu sau:
                  "Nội dung này không nằm trong tài liệu hiện tại nên mình không thể hỗ trợ."

                TỪ KHÓA:
                %s

                NGỮ CẢNH (TRÍCH TỪ TÀI LIỆU):
                %s

                TRẢ LỜI:
                """.formatted(keywords, context);
    }

    /**
     * ✅ Post-filter:
     * - chặn đáp án / giải chi tiết
     * - normalize bullet về "- "
     * - cắt ngắn bullet, giới hạn số dòng
     * - ✅ nếu AI không tuân thủ "ngoài phạm vi" → ép về notInMaterialAnswer
     */
    private String postFilter(String answer, String keywords) {
        if (answer == null) return fallbackVagueHint(keywords);

        String a = answer.trim();
        String lower = a.toLowerCase(Locale.ROOT);

        // hard blocks for "answer revealing"
        if (lower.contains("đáp án") || lower.contains("correct answer")
                || lower.contains("chọn đáp án") || lower.contains("choose option")
                || lower.contains("a)") || lower.contains("b)") || lower.contains("c)") || lower.contains("d)")
                || lower.contains("option a") || lower.contains("option b")) {
            return "Mình không thể đưa ra đáp án. Hãy thử suy nghĩ dựa trên các khái niệm liên quan.";
        }

        // if too long / too detailed patterns => fallback
        if (a.length() > MAX_REPLY_CHARS * 2
                || lower.contains("bước 1") || lower.contains("step 1")
                || lower.contains("đầu tiên") || lower.contains("sau đó")
                || lower.contains("ví dụ") || lower.contains("example")
                || lower.contains("công thức") || lower.contains("formula")
                || lower.contains("code") || lower.contains("implementation")) {
            return fallbackVagueHint(keywords);
        }

        // normalize bullets
        a = normalizeBullets(a);

        // ensure it is bullet list; if not, fallback to vague bullet template
        if (!a.contains("\n") && !a.startsWith("- ")) {
            return fallbackVagueHint(keywords);
        }

        // trim to max bullets + shorten each bullet
        List<String> lines = Arrays.stream(a.split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());

        List<String> bullets = new ArrayList<>();
        for (String line : lines) {
            String b = line;

            // only keep bullet-like content, convert if needed
            if (b.startsWith("- ")) {
                b = b.substring(2).trim();
            } else if (b.startsWith("* ")) {
                b = b.substring(2).trim();
            } else if (b.startsWith("•")) {
                b = b.substring(1).trim();
            } else {
                // skip non-bullet fluff
                continue;
            }

            b = shortenToWords(b, 12);
            if (!b.isBlank()) bullets.add("- " + b);
            if (bullets.size() >= MAX_BULLETS) break;
        }

        if (bullets.size() < MIN_BULLETS) {
            return fallbackVagueHint(keywords);
        }

        String out = String.join("\n", bullets);
        out = truncate(out, MAX_REPLY_CHARS);
        return out;
    }

    private String fallbackVagueHint(String keywords) {
        // template: vague + short + bullet list
        String[] ks = normalizeSpaces(keywords).split("\\s+");
        String k1 = ks.length > 0 ? ks[0] : "khái niệm";
        String k2 = ks.length > 1 ? ks[1] : "ngữ cảnh";
        return String.join("\n",
                "- Xác định " + k1 + " đang được dùng theo nghĩa nào",
                "- Liên hệ " + k2 + " với mục tiêu/ý chính đoạn học",
                "- Nghĩ về điều kiện, ràng buộc hoặc trường hợp đặc biệt",
                "- So sánh với khái niệm gần giống để tránh nhầm",
                "- Tự kiểm tra: định nghĩa, vai trò, và khi nào áp dụng"
        );
    }

    private String normalizeBullets(String s) {
        // convert common bullet formats to "- "
        String x = s.replace("\r\n", "\n");

        // numbered list -> bullet
        x = x.replaceAll("(?m)^\\s*\\d+\\)\\s*", "- ");
        x = x.replaceAll("(?m)^\\s*\\d+\\.\\s*", "- ");

        // asterisks/bullets -> hyphen
        x = x.replaceAll("(?m)^\\s*\\*\\s+", "- ");
        x = x.replaceAll("(?m)^\\s*•\\s+", "- ");

        // ensure lines that begin with "-" have a space
        x = x.replaceAll("(?m)^\\s*-\\s*", "- ");

        return x.trim();
    }

    private String shortenToWords(String s, int maxWords) {
        String t = normalizeSpaces(s);

        // remove trailing punctuation
        t = t.replaceAll("[\\s]+", " ").trim();
        t = t.replaceAll("[,;:.!?]+$", "");

        String[] words = t.split(" ");
        if (words.length <= maxWords) return t;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxWords; i++) {
            if (i > 0) sb.append(" ");
            sb.append(words[i]);
        }
        return sb.toString();
    }

    private String normalizeSpaces(String s) {
        return safe(s).replaceAll("\\s+", " ").trim();
    }

    private ChatMessageResponse toMessageResponse(ChatMessage m) {
        ChatMessageResponse r = new ChatMessageResponse();
        r.setId(m.getId());
        r.setSender(m.getSender());
        r.setContent(m.getContent());
        r.setCreatedAt(m.getCreatedAt());
        return r;
    }

    // ================= UTIL =================

    private String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private ResponseStatusException badRequest(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }

    private ResponseStatusException unauthorized(String m) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, m);
    }

    private ResponseStatusException forbidden(String m) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, m);
    }

    private ResponseStatusException notFound(String m) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, m);
    }
}
