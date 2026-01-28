package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.ai.GeminiResponsesClient;
import com.be_ai_learning_platform.dto.request.PracticeGenerateRequest;
import com.be_ai_learning_platform.dto.request.SubmitPracticeRequest;
import com.be_ai_learning_platform.dto.response.*;
import com.be_ai_learning_platform.entity.*;
import com.be_ai_learning_platform.entity.enums.ExamResult;
import com.be_ai_learning_platform.entity.enums.ExamType;
import com.be_ai_learning_platform.entity.enums.MaterialStatus;
import com.be_ai_learning_platform.entity.enums.QuestionType;
import com.be_ai_learning_platform.repository.*;
import com.be_ai_learning_platform.service.PracticeService;
import com.be_ai_learning_platform.service.QuestionGenerationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PracticeServiceImpl implements PracticeService {

    private static final int DEFAULT_DURATION_MINUTES = 15;
    private static final int DEFAULT_PASS_SCORE = 50;
    private static final int MAX_QUESTIONS = 20;

    private static final int MAX_AI_FEEDBACK_CHARS = 3500;

    private final UserRepository userRepo;
    private final LearningMaterialRepository materialRepo;

    private final ExamRepository examRepo;
    private final ExamAttemptRepository attemptRepo;
    private final QuestionRepository questionRepo;
    private final ExamQuestionRepository examQuestionRepo;

    private final QuestionGenerationService questionGenerationService;

    private final GeminiResponsesClient responsesClient;
    private final ObjectMapper om;
    private final Cache<String, Object> practicePreviewCache;

    public PracticeServiceImpl(
            UserRepository userRepo,
            LearningMaterialRepository materialRepo,
            ExamRepository examRepo,
            ExamAttemptRepository attemptRepo,
            QuestionRepository questionRepo,
            ExamQuestionRepository examQuestionRepo,
            QuestionGenerationService questionGenerationService,
            GeminiResponsesClient responsesClient,
            ObjectMapper om,
            Cache<String, Object> practicePreviewCache
    ) {
        this.userRepo = userRepo;
        this.materialRepo = materialRepo;
        this.examRepo = examRepo;
        this.attemptRepo = attemptRepo;
        this.questionRepo = questionRepo;
        this.examQuestionRepo = examQuestionRepo;
        this.questionGenerationService = questionGenerationService;
        this.responsesClient = responsesClient;
        this.om = om;
        this.practicePreviewCache = practicePreviewCache;
    }

    // =========================
    // Preview (cache)
    // =========================
    @Override
    public GenerateQuestionsResponse generatePreview(String email, PracticeGenerateRequest req) {
        validateGenerateRequest(req);

        User me = getMe(email);

        LearningMaterial material = materialRepo.findByIdAndUser(req.getMaterialId(), me)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));

        if (material.getStatus() != MaterialStatus.EXTRACTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Material is not extracted yet");
        }

        // ✅ Backward compatibility: client gửi token thì thử lấy cache
        String incomingToken = normalizeToken(req.getPreviewToken());
        if (!incomingToken.isBlank()) {
            GenerateQuestionsResponse cached = getCachedPreview(email, incomingToken);
            if (cached != null) return cached;
        }

        // ✅ Generate mới
        GenerateQuestionsResponse generated =
                questionGenerationService.generate(email, req.getMaterialId(), req.getNumberOfQuestions());

        validateGeneratedResponse(generated, req.getNumberOfQuestions());

        // ✅ Server tự tạo token cho preview (source of truth)
        String token = UUID.randomUUID().toString();

        // ✅ set vào response để FE nhận được
        generated.setPreviewToken(token);

        // ✅ cache theo token mới
        practicePreviewCache.put(previewKey(email, token), generated);

        return generated;
    }

    // =========================
    // Start (save mixed questions)
    // =========================
    @Override
    @Transactional
    public StartPracticeResponse start(String email, PracticeGenerateRequest req) {
        validateGenerateRequest(req);

        User me = getMe(email);

        LearningMaterial material = materialRepo.findByIdAndUser(req.getMaterialId(), me)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));

        if (material.getStatus() != MaterialStatus.EXTRACTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Material is not extracted yet");
        }

        // ✅ Strict mode: bắt buộc token để đảm bảo không gọi AI lần 2
        String token = normalizeToken(req.getPreviewToken());
        if (token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "previewToken is required. Please generate preview first.");
        }

        GenerateQuestionsResponse generated = getCachedPreview(email, token);
        if (generated == null) {
            throw new ResponseStatusException(HttpStatus.GONE, "Preview expired. Please generate preview again.");
        }

        validateGeneratedResponse(generated, req.getNumberOfQuestions());

        // ✅ dùng xong xoá cache để tránh reuse
        practicePreviewCache.invalidate(previewKey(email, token));

        /*
        // ============ OPTION: nếu m muốn token optional (không khuyến nghị) ============
        GenerateQuestionsResponse generated = null;

        String token = normalizeToken(req.getPreviewToken());
        if (!token.isBlank()) {
            generated = getCachedPreview(email, token);
        }
        if (generated == null) {
            generated = questionGenerationService.generate(email, req.getMaterialId(), req.getNumberOfQuestions());
        }

        validateGeneratedResponse(generated, req.getNumberOfQuestions());

        if (!token.isBlank()) {
            practicePreviewCache.invalidate(previewKey(email, token));
        }
        // ============================================================================
        */

        Exam exam = new Exam();
        exam.setUser(me);
        exam.setType(ExamType.PRACTICE);
        exam.setDurationMinutes(req.getDurationMinutes() != null ? req.getDurationMinutes() : DEFAULT_DURATION_MINUTES);
        exam.setPassScore(DEFAULT_PASS_SCORE);
        exam.setCreatedAt(LocalDateTime.now());
        exam = examRepo.save(exam);

        for (GeneratedQuestionItemResponse item : generated.getQuestions()) {
            if (item == null || item.getQuestionType() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI response invalid (questionType missing)");
            }

            Question q = new Question();
            q.setMaterial(material);
            q.setQuestionType(item.getQuestionType());
            q.setContent(item.getQuestion() == null ? "" : item.getQuestion().trim());

            if (item.getQuestionType() == QuestionType.MCQ) {
                q.setCorrectAnswer(normalizeChoice(item.getCorrectAnswer()));
                q.setOptionsJson(writeOptionsJson(item.getOptions()));
                q.setAnalysis(safeTrim(item.getAnalysis(), 2000));
            } else {
                // ESSAY: store sampleAnswer in correctAnswer, rubric in analysis as JSON
                q.setCorrectAnswer(safeTrim(item.getSampleAnswer(), 2000));
                q.setOptionsJson(null);
                q.setAnalysis(writeRubricJson(item.getSampleAnswer(), item.getKeywords(), item.getMaxScore()));
            }

            q = questionRepo.save(q);

            ExamQuestion eq = new ExamQuestion();
            eq.setExam(exam);
            eq.setQuestion(q);
            examQuestionRepo.save(eq);
        }

        ExamAttempt attempt = new ExamAttempt();
        attempt.setUser(me);
        attempt.setExam(exam);
        attempt.setClassroom(me.getClassName());
        attempt.setLearningModule(me.getLearningModule());
        attempt.setStartTime(LocalDateTime.now());
        attempt.setStatus(ExamResult.IN_PROGRESS);
        attempt = attemptRepo.save(attempt);

        return new StartPracticeResponse(attempt.getId());
    }

    // =========================
    // Attempt detail
    // =========================
    @Transactional(readOnly = true)
    @Override
    public AttemptDetailResponse getAttempt(String email, Long attemptId) {
        User me = getMe(email);

        ExamAttempt attempt = attemptRepo.findByIdAndUserIdFetchExam(attemptId, me.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));

        Exam exam = attempt.getExam();

        List<ExamQuestion> links = examQuestionRepo.findAllByExamIdFetchQuestion(exam.getId());
        if (links.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Exam has no questions");
        }

        List<AttemptQuestionResponse> questions = links.stream()
                .map(link -> {
                    Question q = link.getQuestion();
                    AttemptQuestionResponse dto = new AttemptQuestionResponse();
                    dto.setQuestionId(q.getId());
                    dto.setQuestionType(q.getQuestionType());
                    dto.setContent(q.getContent());
                    dto.setOptions(q.getQuestionType() == QuestionType.MCQ ? readOptionsJson(q.getOptionsJson()) : null);
                    return dto;
                })
                .toList();

        AttemptDetailResponse res = new AttemptDetailResponse();
        res.setAttemptId(attempt.getId());
        res.setExamId(exam.getId());
        res.setDurationMinutes(exam.getDurationMinutes());
        res.setQuestions(questions);
        return res;
    }

    // =========================
    // Submit: grade (MCQ + ESSAY AI 1-10) + AI feedback tổng
    // =========================
    @Transactional
    @Override
    public SubmitPracticeResponse submit(String email, Long attemptId, SubmitPracticeRequest req) {
        if (req == null || req.getAnswers() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Answers is required");
        }

        User me = getMe(email);

        ExamAttempt attempt = attemptRepo.findByIdAndUserId(attemptId, me.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));

        if (attempt.getSubmitTime() != null
                || attempt.getStatus() == ExamResult.SUBMITTED
                || attempt.getStatus() == ExamResult.PASSED
                || attempt.getStatus() == ExamResult.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attempt already submitted");
        }

        if (attempt.getStatus() != ExamResult.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attempt is not in progress");
        }

        Exam exam = attempt.getExam();

        List<ExamQuestion> links = examQuestionRepo.findAllByExamIdFetchQuestion(exam.getId());
        List<Question> examQuestions = links.stream().map(ExamQuestion::getQuestion).toList();
        if (examQuestions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Exam has no questions");
        }

        Map<Long, SubmitPracticeRequest.AnswerItem> answerByQid = req.getAnswers().stream()
                .filter(a -> a.getQuestionId() != null)
                .collect(Collectors.toMap(
                        SubmitPracticeRequest.AnswerItem::getQuestionId,
                        a -> a,
                        (a, b) -> b
                ));

        List<AnswerResult> results = new ArrayList<>();

        int totalEarned = 0;
        int totalMax = 0;

        for (Question q : examQuestions) {
            SubmitPracticeRequest.AnswerItem ans = answerByQid.get(q.getId());

            if (q.getQuestionType() == QuestionType.MCQ) {
                String sel = ans != null ? normalizeChoice(ans.getSelectedAnswer()) : "";
                if (!isValidChoice(sel)) sel = "";

                String right = normalizeChoice(q.getCorrectAnswer());
                boolean isCorrect = !sel.isBlank() && sel.equals(right);

                int maxScore = 1;
                int score = isCorrect ? 1 : 0;

                results.add(AnswerResult.forMcq(
                        q.getId(),
                        sel,
                        right,
                        score,
                        maxScore,
                        isCorrect ? "Đúng ✅" : "Sai ❌"
                ));

                totalEarned += score;
                totalMax += maxScore;

            } else {
                String textAnswer = ans != null ? safeTrim(ans.getTextAnswer(), 5000) : "";
                Rubric rubric = readRubric(q.getAnalysis(), q.getCorrectAnswer());

                // requirement: AI chấm thang 1-10 cho ESSAY
                rubric.maxScore = 10;
                int maxScore = 10;

                int score;
                String perQuestionFeedback;

                try {
                    AiEssayGrade g = gradeEssayByAi(q, textAnswer, rubric);
                    score = g.score; // 1..10
                    perQuestionFeedback = buildEssayFeedbackText(g);
                } catch (Exception aiErr) {
                    // fallback rule-based nếu AI lỗi/quota/parse fail
                    EssayScore fb = scoreEssay(textAnswer, rubric);
                    score = Math.max(0, Math.min(fb.score, 10));
                    perQuestionFeedback = (fb.feedback == null ? "" : fb.feedback) + " (fallback: AI tạm lỗi)";
                }

                results.add(AnswerResult.forEssay(
                        q.getId(),
                        textAnswer,
                        rubric.sampleAnswer,
                        score,
                        maxScore,
                        perQuestionFeedback
                ));

                totalEarned += score;
                totalMax += maxScore;
            }
        }

        int scorePct = (int) Math.round((totalEarned * 100.0) / Math.max(totalMax, 1));

        attempt.setAnswersJson(writeAnswersJson(results));
        attempt.setScore(scorePct);
        attempt.setSubmitTime(LocalDateTime.now());

        int pass = exam.getPassScore() != null ? exam.getPassScore() : DEFAULT_PASS_SCORE;
        attempt.setStatus(scorePct >= pass ? ExamResult.PASSED : ExamResult.FAILED);

        // AI feedback tổng (sau khi grade)
        // AI feedback tổng (sau khi grade)
        String aiFeedback = "";
        try {
            String prompt = buildAiFeedbackPrompt(examQuestions, results, scorePct);
            aiFeedback = safeTrim(responsesClient.generateText(prompt), MAX_AI_FEEDBACK_CHARS);
        } catch (Exception e) {
            // ✅ log để debug (quota/timeout/key/model...)
            e.printStackTrace();
        }

// ✅ fallback để FE luôn có nội dung
        if (aiFeedback == null || aiFeedback.isBlank()) {
            aiFeedback = """
AI feedback tạm thời chưa sẵn sàng (có thể do quota/timeout).
Bạn có thể bấm “Xem lại đáp án” để xem nhận xét chi tiết từng câu (đúng/sai + giải thích + gợi ý ôn lại).
""".trim();
        }

        attempt.setAiFeedback(aiFeedback);

        attemptRepo.save(attempt);

        boolean timedOut = false;
        if (exam.getDurationMinutes() != null && attempt.getStartTime() != null) {
            LocalDateTime deadline = attempt.getStartTime().plusMinutes(exam.getDurationMinutes());
            timedOut = LocalDateTime.now().isAfter(deadline);
        }

        SubmitPracticeResponse res = new SubmitPracticeResponse();
        res.setScore(scorePct);
        res.setEarnedPoints(totalEarned);
        res.setTotalPoints(totalMax);
        res.setStatus(attempt.getStatus());
        res.setTimedOut(timedOut);

        res.setFeedback(buildStaticFeedback(scorePct));
        res.setAiFeedback(aiFeedback);
        return res;
    }

    // =========================
    // Review
    // =========================
    @Transactional(readOnly = true)
    @Override
    public AttemptReviewResponse getReview(String email, Long attemptId) {
        User me = getMe(email);

        ExamAttempt attempt = attemptRepo.findByIdAndUserIdFetchExam(attemptId, me.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));

        if (attempt.getSubmitTime() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attempt is not submitted yet");
        }

        List<AnswerResult> stored = readAnswersJson(attempt.getAnswersJson());
        if (stored.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No stored answers for review");
        }
        Map<Long, AnswerResult> storedMap = stored.stream()
                .collect(Collectors.toMap(a -> a.questionId, a -> a, (a, b) -> b));

        Exam exam = attempt.getExam();

        List<ExamQuestion> links = examQuestionRepo.findAllByExamIdFetchQuestion(exam.getId());
        if (links.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Exam has no questions");
        }

        int total = links.size();
        int correctCount = 0;

        List<AttemptReviewItemResponse> items = new ArrayList<>();

        for (ExamQuestion link : links) {
            Question q = link.getQuestion();
            AnswerResult ar = storedMap.get(q.getId());

            AttemptReviewItemResponse item = new AttemptReviewItemResponse();
            item.setQuestionId(q.getId());
            item.setQuestionType(q.getQuestionType());
            item.setContent(q.getContent());

            if (q.getQuestionType() == QuestionType.MCQ) {
                item.setOptions(readOptionsJson(q.getOptionsJson()));
                String right = normalizeChoice(q.getCorrectAnswer());
                String sel = ar != null ? normalizeChoice(ar.selectedAnswer) : "";

                boolean isCorrect = !sel.isBlank() && sel.equals(right);
                if (isCorrect) correctCount++;

                item.setCorrectAnswer(right);
                item.setSelectedAnswer(sel);
                item.setIsCorrect(isCorrect);

                item.setScore(ar != null ? ar.score : 0);
                item.setMaxScore(ar != null ? ar.maxScore : 1);
                item.setFeedback(ar != null ? ar.feedback : "");

            } else {
                Rubric rubric = readRubric(q.getAnalysis(), q.getCorrectAnswer());

                item.setOptions(null);
                item.setYourAnswer(ar != null ? ar.textAnswer : "");
                item.setSampleAnswer(rubric.sampleAnswer);

                int score = ar != null ? ar.score : 0;
                int max = ar != null ? ar.maxScore : rubric.maxScore;

                item.setScore(score);
                item.setMaxScore(max);
                item.setIsCorrect(score >= max);
                item.setFeedback(ar != null ? ar.feedback : "");
            }

            items.add(item);
        }

        AttemptReviewResponse res = new AttemptReviewResponse();
        res.setAttemptId(attempt.getId());
        res.setScore(attempt.getScore());
        res.setTotalQuestions(total);
        res.setCorrectCount(correctCount);
        res.setAiFeedback(attempt.getAiFeedback());
        res.setItems(items);
        return res;
    }

    // =========================
    // Internal helper models
    // =========================
    private static class AnswerResult {
        public Long questionId;
        public QuestionType questionType;

        // MCQ
        public String selectedAnswer;
        public String correctAnswer;

        // ESSAY
        public String textAnswer;
        public String sampleAnswer;

        public Integer score;
        public Integer maxScore;
        public String feedback;

        public static AnswerResult forMcq(Long qid, String selected, String correct, int score, int max, String feedback) {
            AnswerResult r = new AnswerResult();
            r.questionId = qid;
            r.questionType = QuestionType.MCQ;
            r.selectedAnswer = selected;
            r.correctAnswer = correct;
            r.score = score;
            r.maxScore = max;
            r.feedback = feedback;
            return r;
        }

        public static AnswerResult forEssay(Long qid, String textAnswer, String sampleAnswer, int score, int max, String feedback) {
            AnswerResult r = new AnswerResult();
            r.questionId = qid;
            r.questionType = QuestionType.ESSAY;
            r.textAnswer = textAnswer;
            r.sampleAnswer = sampleAnswer;
            r.score = score;
            r.maxScore = max;
            r.feedback = feedback;
            return r;
        }
    }

    private static class Rubric {
        public String sampleAnswer;
        public List<String> keywords;
        public int maxScore;

        public Rubric(String sampleAnswer, List<String> keywords, int maxScore) {
            this.sampleAnswer = sampleAnswer;
            this.keywords = keywords;
            this.maxScore = maxScore;
        }
    }

    private static class EssayScore {
        public int score;
        public String feedback;

        public EssayScore(int score, String feedback) {
            this.score = score;
            this.feedback = feedback;
        }
    }

    private static class AiEssayGrade {
        public Integer score;            // 1..10
        public String explanation;       // giải thích
        public List<String> needReview;  // gợi ý ôn
        public AiEssayGrade() {}
    }

    // =========================
    // ESSAY: AI grading helpers
    // =========================
    private AiEssayGrade gradeEssayByAi(Question q, String userAnswer, Rubric rubric) {
        String prompt = """
Bạn là giám khảo chấm câu hỏi tự luận ngắn.
Chỉ dựa vào câu hỏi + đáp án mẫu + bài làm học viên. Không bịa thêm kiến thức ngoài phạm vi.
Chấm điểm thang 1-10 (1 là rất kém, 10 là rất tốt).

Trả về DUY NHẤT một JSON hợp lệ theo format:
{
  "score": 1,
  "explanation": "giải thích ngắn gọn vì sao điểm như vậy, chỉ ra thiếu/sai ở đâu",
  "needReview": ["chủ đề 1", "chủ đề 2"]
}

Câu hỏi: %s

Đáp án mẫu: %s

Keywords rubric: %s

Bài làm học viên: %s
""".formatted(
                safeTrim(q.getContent(), 1500),
                safeTrim(rubric.sampleAnswer, 2000),
                String.join(", ", rubric.keywords == null ? List.of() : rubric.keywords),
                safeTrim(userAnswer, 5000)
        );

        String raw = responsesClient.generateText(prompt);
        raw = extractJsonObject(raw);

        try {
            AiEssayGrade g = om.readValue(raw, AiEssayGrade.class);

            int score = g.score == null ? 1 : g.score;
            if (score < 1) score = 1;
            if (score > 10) score = 10;

            g.score = score;
            if (g.explanation == null) g.explanation = "";
            if (g.needReview == null) g.needReview = List.of();
            return g;

        } catch (Exception e) {
            throw new RuntimeException("AI essay grade parse failed: " + safeTrim(raw, 300), e);
        }
    }

    private String buildEssayFeedbackText(AiEssayGrade g) {
        StringBuilder sb = new StringBuilder();
        if (g.explanation != null && !g.explanation.isBlank()) {
            sb.append(g.explanation.trim());
        }
        if (g.needReview != null && !g.needReview.isEmpty()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("Nên đọc lại: ").append(String.join(", ", g.needReview));
        }
        return sb.toString().trim();
    }

    private String extractJsonObject(String text) {
        if (text == null) return "";
        String t = text.trim();

        if (t.startsWith("```")) {
            t = t.replaceFirst("^```[a-zA-Z]*\\s*", "");
            t = t.replaceFirst("\\s*```\\s*$", "");
            t = t.trim();
        }

        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start >= 0 && end > start) {
            t = t.substring(start, end + 1).trim();
        }

        t = t.replaceAll(",\\s*([}\\]])", "$1");
        return t;
    }

    // =========================
    // Store rubric as JSON in Question.analysis for ESSAY
    // =========================
    private String writeRubricJson(String sampleAnswer, List<String> keywords, Integer maxScore) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sampleAnswer", safeTrim(sampleAnswer, 2000));
        data.put("keywords", keywords == null ? List.of() : keywords);
        data.put("maxScore", maxScore == null ? 10 : maxScore);
        try {
            return om.writeValueAsString(data);
        } catch (Exception e) {
            return "";
        }
    }

    private Rubric readRubric(String analysisJson, String fallbackSampleAnswer) {
        try {
            if (analysisJson == null || analysisJson.isBlank()) {
                return new Rubric(fallbackSampleAnswer == null ? "" : fallbackSampleAnswer, List.of(), 10);
            }
            Map<String, Object> m = om.readValue(analysisJson, new TypeReference<Map<String, Object>>() {});
            String sample = String.valueOf(m.getOrDefault("sampleAnswer",
                    fallbackSampleAnswer == null ? "" : fallbackSampleAnswer));
            @SuppressWarnings("unchecked")
            List<String> keywords = (List<String>) m.getOrDefault("keywords", List.of());
            int maxScore = Integer.parseInt(String.valueOf(m.getOrDefault("maxScore", 10)));
            if (maxScore <= 0) maxScore = 10;
            return new Rubric(sample, keywords == null ? List.of() : keywords, maxScore);
        } catch (Exception e) {
            return new Rubric(fallbackSampleAnswer == null ? "" : fallbackSampleAnswer, List.of(), 10);
        }
    }

    // fallback rule-based
    private EssayScore scoreEssay(String answer, Rubric rubric) {
        String a = answer == null ? "" : answer.trim();
        if (a.isBlank()) {
            return new EssayScore(0, "Chưa trả lời.");
        }

        List<String> kws = rubric.keywords == null ? List.of() : rubric.keywords;
        if (kws.isEmpty()) {
            int s = Math.max(1, (int) Math.round(rubric.maxScore * 0.5));
            return new EssayScore(s, "Có trả lời nhưng rubric chưa đủ rõ, hệ thống chấm tạm theo mức trung bình.");
        }

        String lower = a.toLowerCase(Locale.ROOT);
        List<String> missing = new ArrayList<>();
        int hit = 0;

        for (String kw : kws) {
            if (kw == null || kw.isBlank()) continue;
            String k = kw.toLowerCase(Locale.ROOT).trim();
            if (lower.contains(k)) hit++;
            else missing.add(kw);
        }

        double ratio = hit * 1.0 / Math.max(kws.size(), 1);
        int score = (int) Math.round(ratio * rubric.maxScore);

        String fb;
        if (missing.isEmpty()) fb = "Tốt ✅ Đủ ý chính theo rubric.";
        else fb = "Thiếu ý: " + String.join(", ", missing);

        return new EssayScore(score, fb);
    }

    private String writeAnswersJson(List<AnswerResult> results) {
        try {
            return om.writeValueAsString(results);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<AnswerResult> readAnswersJson(String json) {
        try {
            if (json == null || json.isBlank()) return List.of();
            return om.readValue(json, new TypeReference<List<AnswerResult>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String buildAiFeedbackPrompt(List<Question> questions, List<AnswerResult> results, int scorePct) {
        Map<Long, AnswerResult> map = results.stream()
                .collect(Collectors.toMap(a -> a.questionId, a -> a, (a, b) -> b));

        StringBuilder sb = new StringBuilder();
        sb.append("""
Bạn là trợ giảng. Hãy nhận xét bài làm của học viên NGẮN GỌN nhưng SẮC NÉT bằng tiếng Việt.
Mục tiêu: chỉ ra câu sai, vì sao sai (dựa trên đáp án đúng/đáp án mẫu), và gợi ý ôn tập.
Không bịa kiến thức ngoài phạm vi câu hỏi.
Định dạng:
- Tổng quan (2-3 gạch đầu dòng)
- Lỗi sai nổi bật (liệt kê theo số câu)
- Gợi ý cải thiện (3-5 gạch đầu dòng)

Điểm tổng: """).append(scorePct).append("/100\n\n");

        int idx = 1;
        for (Question q : questions) {
            AnswerResult ar = map.get(q.getId());
            sb.append("Câu ").append(idx++).append(" (").append(q.getQuestionType()).append("): ")
                    .append(q.getContent()).append("\n");

            if (q.getQuestionType() == QuestionType.MCQ) {
                sb.append("- Đáp án đúng: ").append(normalizeChoice(q.getCorrectAnswer())).append("\n");
                sb.append("- Học viên chọn: ").append(ar != null ? safeStr(ar.selectedAnswer) : "").append("\n");
            } else {
                Rubric rubric = readRubric(q.getAnalysis(), q.getCorrectAnswer());
                sb.append("- Đáp án mẫu: ").append(rubric.sampleAnswer).append("\n");
                sb.append("- Học viên trả lời: ").append(ar != null ? safeStr(ar.textAnswer) : "").append("\n");
                sb.append("- Keywords rubric: ")
                        .append(String.join(", ", rubric.keywords == null ? List.of() : rubric.keywords))
                        .append("\n");
            }

            sb.append("- Chấm: ").append(ar != null ? ar.score : 0)
                    .append("/")
                    .append(ar != null ? ar.maxScore : 1)
                    .append("\n\n");
        }
        return sb.toString();
    }

    // =========================
    // Helpers
    // =========================
    private void validateGenerateRequest(PracticeGenerateRequest req) {
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is required");
        if (req.getMaterialId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "materialId is required");
        if (req.getNumberOfQuestions() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "numberOfQuestions is required");

        int n = req.getNumberOfQuestions();
        if (n <= 0 || n > MAX_QUESTIONS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "numberOfQuestions must be 1.." + MAX_QUESTIONS);
        }
    }

    private void validateGeneratedResponse(GenerateQuestionsResponse res, int expected) {
        if (res == null || res.getQuestions() == null || res.getQuestions().size() != expected) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI must return exactly " + expected + " questions");
        }
    }

    private User getMe(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private String previewKey(String email, String token) {
        return "practice_preview:" + email + ":" + token;
    }

    private GenerateQuestionsResponse getCachedPreview(String email, String token) {
        Object cached = practicePreviewCache.getIfPresent(previewKey(email, token));
        if (cached instanceof GenerateQuestionsResponse r) return r;
        return null;
    }

    private String normalizeToken(String token) {
        return token == null ? "" : token.trim();
    }

    private String buildStaticFeedback(int score) {
        if (score >= 85) return "Rất tốt! Bạn nắm kiến thức khá vững.";
        if (score >= 70) return "Khá ổn! Nên xem lại các câu sai để củng cố.";
        if (score >= 50) return "Tạm đạt. Bạn nên ôn lại các phần trọng tâm.";
        return "Chưa đạt. Hãy ôn lại tài liệu và làm lại để cải thiện.";
    }

    private String normalizeChoice(String s) {
        if (s == null) return "";
        return s.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isValidChoice(String s) {
        return List.of("A", "B", "C", "D").contains(s);
    }

    private String safeTrim(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private String safeStr(String s) {
        return s == null ? "" : s;
    }

    private String writeOptionsJson(Map<String, String> options) {
        try {
            return om.writeValueAsString(options == null ? Map.of() : options);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, String> readOptionsJson(String json) {
        try {
            if (json == null || json.isBlank()) return Map.of();
            return om.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
