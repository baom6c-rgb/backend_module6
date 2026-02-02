package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.ai.GeminiResponsesClient;
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

    private final GeminiResponsesClient responsesClient;
    private final UserRepository userRepo;
    private final LearningMaterialRepository materialRepo;
    private final ChatSessionRepository sessionRepo;
    private final ChatMessageRepository messageRepo;

    // ===== US17 guardrails =====
    private static final int MAX_KEYWORDS_CHARS = 80;
    private static final int MAX_KEYWORDS_WORDS = 8;
    private static final int MIN_RELEVANT_HITS = 1;

    private static final int MAX_CONTEXT_CHARS = 1800;

    // ✅ make reply shorter & vaguer
    private static final int MAX_REPLY_CHARS = 520;
    private static final int MAX_BULLETS = 5;
    private static final int MIN_BULLETS = 3;

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

        // relevance check
        int hits = countKeywordHits(material.getExtractedText(), validatedKeywords);
        if (hits < MIN_RELEVANT_HITS) {
            throw badRequest("Chỉ được hỏi các từ khóa liên quan đến nội dung bài học hiện tại.");
        }

        // save user message
        saveMessage(session, SenderType.USER, validatedKeywords);

        String context = buildContext(material.getExtractedText(), validatedKeywords);
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

    private int countKeywordHits(String text, String keywords) {
        if (text == null || text.isBlank()) return 0;
        String hay = normalize(text);
        int hits = 0;
        for (String k : keywords.split("\\s+")) {
            if (hay.contains(normalize(k))) hits++;
        }
        return hits;
    }

    private String buildContext(String extractedText, String keywords) {
        if (extractedText == null) return "";

        Set<String> keys = Arrays.stream(keywords.split("\\s+"))
                .map(this::normalize)
                .collect(Collectors.toSet());

        StringBuilder sb = new StringBuilder();
        for (String para : extractedText.split("\\n\\s*\\n")) {
            String pNorm = normalize(para);
            if (keys.stream().anyMatch(pNorm::contains)) {
                sb.append(para).append("\n---\n");
                if (sb.length() > MAX_CONTEXT_CHARS) break;
            }
        }
        return truncate(sb.toString(), MAX_CONTEXT_CHARS);
    }

    /**
     * ✅ Prompt: ép AI trả lời NGẮN + MƠ HỒ + GỢI Ý
     * - 3–5 bullet, bắt đầu bằng "- "
     * - mỗi bullet <= 12 từ
     * - không ví dụ cụ thể, không step-by-step, không code
     * - không đáp án / không chọn A/B/C/D
     */
    private String buildPrompt(String keywords, String context) {
        return """
                Bạn là trợ giảng trong lúc học viên đang làm bài.
                NHIỆM VỤ: chỉ đưa gợi ý mơ hồ để học viên tự suy nghĩ.

                RÀNG BUỘC BẮT BUỘC:
                - Chỉ dựa trên NGỮ CẢNH được cung cấp (không kiến thức ngoài).
                - KHÔNG đưa đáp án, KHÔNG giải bài, KHÔNG chọn A/B/C/D.
                - KHÔNG hướng dẫn từng bước, KHÔNG code, KHÔNG công thức chi tiết.
                - Viết thật NGẮN: 3 đến 5 gạch đầu dòng.
                - Mỗi gạch đầu dòng tối đa 12 từ.
                - Dùng đúng format: mỗi dòng bắt đầu bằng "- ".

                TỪ KHÓA: %s

                NGỮ CẢNH (trích):
                %s

                TRẢ LỜI (chỉ 3-5 dòng, mỗi dòng bắt đầu "- "):
                """.formatted(keywords, context);
    }

    /**
     * ✅ Post-filter:
     * - chặn đáp án / giải chi tiết
     * - normalize bullet về "- "
     * - cắt ngắn bullet, giới hạn số dòng
     */
    private String postFilter(String answer, String keywords) {
        if (answer == null) return fallbackVagueHint(keywords);

        String a = answer.trim();

        // hard blocks for "answer revealing"
        String lower = a.toLowerCase(Locale.ROOT);
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
