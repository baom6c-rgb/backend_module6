package com.be_ai_learning_platform.service.validator;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ProgrammingContentValidator {

    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    private static final Pattern QUESTION_COUNT_REQUEST_PATTERN = Pattern.compile(
            "\\b(?:tao|tạo|generate|make|create|cho|viet|viết)?\\s*\\d+\\s*(?:cau hoi|câu hỏi|questions?|question)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Set<String> PROGRAMMING_KEYWORDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(List.of(
                    "java", "javascript", "js", "typescript", "ts", "react", "vue", "angular",
                    "spring", "spring boot", "spring mvc", "spring security",
                    "hibernate", "jpa", "jdbc",
                    "sql", "mysql", "postgresql", "postgres", "oracle", "mongodb", "redis",
                    "html", "css", "scss", "tailwind", "bootstrap",
                    "node", "nodejs", "express", "nestjs",
                    "api", "rest", "restful", "graphql", "json", "jwt", "oauth", "auth",
                    "backend", "frontend", "fullstack",
                    "controller", "service", "repository", "entity", "dto", "mapper",
                    "class", "object", "interface", "abstract", "extends", "implements",
                    "function", "method", "variable", "const", "let", "var",
                    "array", "string", "number", "boolean", "null", "undefined",
                    "loop", "for", "while", "if", "else", "switch",
                    "exception", "try catch", "debug", "bug", "fix bug",
                    "algorithm", "data structure", "stack", "queue", "tree", "graph",
                    "docker", "kubernetes", "git", "gradle", "maven",
                    "http", "https", "endpoint", "request", "response", "cors",
                    "validate", "validation", "unit test", "integration test",

                    "lap trinh", "code", "mang", "ham", "bien", "vong lap",
                    "doi tuong", "lop", "thuat toan", "cau truc du lieu",
                    "xac thuc", "bao mat", "dang nhap",

                    "lập trình", "mảng", "hàm", "biến", "vòng lặp",
                    "đối tượng", "lớp", "thuật toán", "cấu trúc dữ liệu",
                    "xác thực", "bảo mật", "đăng nhập"
            )));

    private static final List<String> OBVIOUS_NON_PROGRAMMING_KEYWORDS = List.of(
            "gia vang", "giá vàng", "vang sjc",
            "thoi tiet", "thời tiết",
            "tu vi", "tử vi", "boi bai", "bói bài", "tarot",
            "tinh yeu", "tình yêu", "nguoi yeu", "người yêu",
            "bong da", "bóng đá", "ket qua bong da", "kết quả bóng đá",
            "chung khoan", "chứng khoán", "coin", "bitcoin", "crypto",
            "gia xang", "giá xăng",
            "tin tuc", "tin tức", "thoi su", "thời sự",
            "am nhac", "âm nhạc", "phim", "ca si", "ca sĩ"
    );

    public String validateAndNormalize(String input) {
        String raw = input == null ? "" : input.trim();
        if (raw.isBlank()) {
            throw new IllegalArgumentException("Nội dung không được để trống.");
        }

        String normalized = normalize(raw);

        validateNoQuestionCountInstruction(normalized);

        if (looksLikeCode(raw)) {
            return raw;
        }

        if (containsAny(normalized, OBVIOUS_NON_PROGRAMMING_KEYWORDS) && !containsProgrammingSignal(normalized)) {
            throw new IllegalArgumentException(buildProgrammingOnlyMessage());
        }

        if (!containsProgrammingSignal(normalized)) {
            throw new IllegalArgumentException(buildProgrammingOnlyMessage());
        }

        return raw;
    }

    public void validateOnly(String input) {
        validateAndNormalize(input);
    }

    private void validateNoQuestionCountInstruction(String normalized) {
        if (normalized == null || normalized.isBlank()) return;

        if (QUESTION_COUNT_REQUEST_PATTERN.matcher(normalized).find()) {
            throw new IllegalArgumentException(
                    "Bạn không cần nhập số lượng câu hỏi. Hệ thống sẽ dùng số câu cố định theo Admin Settings. "
                            + "Ví dụ đúng: 'tạo câu hỏi liên quan đến mảng trong JavaScript'."
            );
        }
    }

    private boolean containsProgrammingSignal(String normalized) {
        for (String keyword : PROGRAMMING_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String normalized, List<String> keywords) {
        for (String keyword : keywords) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeCode(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String t = text.trim();

        if (t.contains("\n")) {
            String[] codeHints = {
                    "{", "}", ";", "=>", "function ", "const ", "let ", "var ",
                    "import ", "export ", "class ", "interface ",
                    "public ", "private ", "protected ",
                    "@RestController", "@Service", "@Repository",
                    "System.out.println", "console.log",
                    "return ", "if (", "for (", "while ("
            };

            for (String hint : codeHints) {
                if (t.contains(hint)) {
                    return true;
                }
            }
        }

        String lower = t.toLowerCase(Locale.ROOT);
        return lower.contains("public static void main")
                || lower.contains("console.log(")
                || lower.contains("useeffect(")
                || lower.contains("usestate(")
                || lower.contains("select * from")
                || lower.contains("insert into")
                || lower.contains("@getmapping")
                || lower.contains("@postmapping");
    }

    private String normalize(String text) {
        String lower = text.toLowerCase(Locale.ROOT).trim();
        String noAccent = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return MULTI_SPACE.matcher(noAccent).replaceAll(" ");
    }

    private String buildProgrammingOnlyMessage() {
        return "Chỉ hỗ trợ tạo đề từ nội dung liên quan đến lập trình / phát triển phần mềm. "
                + "Ví dụ hợp lệ: 'tạo câu hỏi về mảng trong JavaScript', 'Spring Boot JWT', 'React useEffect'.";
    }
}