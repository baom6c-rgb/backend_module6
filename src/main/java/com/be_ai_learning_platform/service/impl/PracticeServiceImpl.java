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
    private static final int MAX_QUESTIONS = 30;

    private final UserRepository userRepo;
    private final LearningMaterialRepository materialRepo;

    private final ExamRepository examRepo;
    private final ExamAttemptRepository attemptRepo;
    private final QuestionRepository questionRepo;
    private final ExamQuestionRepository examQuestionRepo;

    private final QuestionGenerationService questionGenerationService;
    private final GeminiResponsesClient responsesClient;

    private final ObjectMapper om = new ObjectMapper();

    public PracticeServiceImpl(
            UserRepository userRepo,
            LearningMaterialRepository materialRepo,
            ExamRepository examRepo,
            ExamAttemptRepository attemptRepo,
            QuestionRepository questionRepo,
            ExamQuestionRepository examQuestionRepo,
            QuestionGenerationService questionGenerationService,
            GeminiResponsesClient responsesClient
    ) {
        this.userRepo = userRepo;
        this.materialRepo = materialRepo;
        this.examRepo = examRepo;
        this.attemptRepo = attemptRepo;
        this.questionRepo = questionRepo;
        this.examQuestionRepo = examQuestionRepo;
        this.questionGenerationService = questionGenerationService;
        this.responsesClient = responsesClient;
    }

    @Override
    public GenerateQuestionsResponse generatePreview(String email, PracticeGenerateRequest req) {
        validateGenerateRequest(req);

        User me = getMe(email);

        LearningMaterial material = materialRepo.findByIdAndUser(req.getMaterialId(), me)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));

        if (material.getStatus() != MaterialStatus.EXTRACTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Material is not extracted yet");
        }

        GenerateQuestionsResponse res =
                questionGenerationService.generate(email, req.getMaterialId(), req.getNumberOfQuestions());

        validateGeneratedResponse(res, req.getNumberOfQuestions());

        return res;
    }

    @Transactional
    @Override
    public StartPracticeResponse start(String email, PracticeGenerateRequest req) {
        validateGenerateRequest(req);

        User me = getMe(email);

        LearningMaterial material = materialRepo.findByIdAndUser(req.getMaterialId(), me)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));

        if (material.getStatus() != MaterialStatus.EXTRACTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Material is not extracted yet");
        }

        // 1) Generate questions
        GenerateQuestionsResponse generated =
                questionGenerationService.generate(email, req.getMaterialId(), req.getNumberOfQuestions());

        validateGeneratedResponse(generated, req.getNumberOfQuestions());

        // 2) Create Exam (PRACTICE)
        Exam exam = new Exam();
        exam.setUser(me);
        exam.setType(ExamType.PRACTICE);
        exam.setDurationMinutes(req.getDurationMinutes() != null ? req.getDurationMinutes() : DEFAULT_DURATION_MINUTES);
        exam.setPassScore(DEFAULT_PASS_SCORE);
        exam.setCreatedAt(LocalDateTime.now());
        exam = examRepo.save(exam);

        // 3) Save questions
        for (GeneratedQuestionItemResponse item : generated.getQuestions()) {
            Question q = new Question();
            q.setMaterial(material);
            q.setQuestionType(QuestionType.MCQ);
            q.setContent(item.getQuestion().trim());
            q.setCorrectAnswer(normalizeChoice(item.getCorrectAnswer()));
            q.setOptionsJson(writeOptionsJson(item.getOptions()));
            q = questionRepo.save(q);

            ExamQuestion eq = new ExamQuestion();
            eq.setExam(exam);
            eq.setQuestion(q);
            examQuestionRepo.save(eq);
        }

        // 4) Create attempt
        ExamAttempt attempt = new ExamAttempt();
        attempt.setUser(me);
        attempt.setExam(exam);
        attempt.setStartTime(LocalDateTime.now());
        attempt.setStatus(ExamResult.IN_PROGRESS);
        attempt = attemptRepo.save(attempt);

        return new StartPracticeResponse(attempt.getId());
    }

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
                    dto.setContent(q.getContent());
                    dto.setOptions(readOptionsJson(q.getOptionsJson()));
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

        List<ExamQuestion> links = examQuestionRepo.findAllByExamId(exam.getId());
        List<Question> examQuestions = links.stream().map(ExamQuestion::getQuestion).toList();

        if (examQuestions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Exam has no questions");
        }

        // map answers by questionId
        Map<Long, String> selected = req.getAnswers().stream()
                .filter(a -> a.getQuestionId() != null)
                .collect(Collectors.toMap(
                        SubmitPracticeRequest.AnswerItem::getQuestionId,
                        a -> normalizeChoice(a.getSelectedAnswer()),
                        (a, b) -> b
                ));

        // ✅ store answers for review
        attempt.setAnswersJson(writeSelectedAnswersJson(selected));

        int total = examQuestions.size();
        int correct = 0;
        List<String> wrongSummaries = new ArrayList<>();

        for (Question q : examQuestions) {
            String sel = selected.getOrDefault(q.getId(), "");
            if (!isValidChoice(sel)) sel = "";

            String right = normalizeChoice(q.getCorrectAnswer());

            if (!sel.isBlank() && sel.equals(right)) {
                correct++;
            } else {
                wrongSummaries.add("- " + safe(q.getContent()) +
                        " (Đúng: " + right + ", Bạn chọn: " + (sel.isBlank() ? "Bỏ trống" : sel) + ")");
            }
        }

        int score = (int) Math.round((correct * 100.0) / Math.max(total, 1));

        // update attempt
        attempt.setScore(score);
        attempt.setSubmitTime(LocalDateTime.now());
        attempt.setStatus(ExamResult.SUBMITTED);

        int pass = exam.getPassScore() != null ? exam.getPassScore() : DEFAULT_PASS_SCORE;
        attempt.setStatus(score >= pass ? ExamResult.PASSED : ExamResult.FAILED);

        attemptRepo.save(attempt);

        // AI feedback (Gemini)
        String feedbackPrompt = buildFeedbackPrompt(score, correct, total, wrongSummaries);
        String feedback;
        try {
            feedback = responsesClient.generateText(feedbackPrompt);
        } catch (Exception e) {
            feedback = "Bạn đã hoàn thành bài ôn tập. Hãy xem lại các câu sai và thử làm lại để cải thiện điểm nhé!";
        }

        SubmitPracticeResponse res = new SubmitPracticeResponse();
        res.setScore(score);
        res.setFeedback(feedback);
        return res;
    }

    @Transactional(readOnly = true)
    @Override
    public AttemptReviewResponse getReview(String email, Long attemptId) {
        User me = getMe(email);

        ExamAttempt attempt = attemptRepo.findByIdAndUserIdFetchExam(attemptId, me.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));

        if (attempt.getSubmitTime() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attempt is not submitted yet");
        }

        Map<Long, String> selected = readSelectedAnswersJson(attempt.getAnswersJson());
        if (selected.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No stored answers for review");
        }

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

            String right = normalizeChoice(q.getCorrectAnswer());
            String sel = normalizeChoice(selected.getOrDefault(q.getId(), ""));

            boolean isCorrect = !sel.isBlank() && sel.equals(right);
            if (isCorrect) correctCount++;

            AttemptReviewItemResponse item = new AttemptReviewItemResponse();
            item.setQuestionId(q.getId());
            item.setContent(q.getContent());
            item.setOptions(readOptionsJson(q.getOptionsJson()));
            item.setCorrectAnswer(right);
            item.setSelectedAnswer(sel.isBlank() ? "" : sel);
            item.setIsCorrect(isCorrect);

            try {
                item.setExplanation(q.getAnalysis() != null ? q.getAnalysis() : "");
            } catch (Exception ignore) {
                item.setExplanation("");
            }

            items.add(item);
        }

        AttemptReviewResponse res = new AttemptReviewResponse();
        res.setAttemptId(attempt.getId());
        res.setScore(attempt.getScore() != null ? attempt.getScore() : 0);
        res.setTotalQuestions(total);
        res.setCorrectCount(correctCount);
        res.setItems(items);
        return res;
    }

    // ===== helpers =====

    private void validateGenerateRequest(PracticeGenerateRequest req) {
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body is required");
        if (req.getMaterialId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "materialId is required");
        if (req.getNumberOfQuestions() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "numberOfQuestions is required");
        if (req.getNumberOfQuestions() < 1 || req.getNumberOfQuestions() > MAX_QUESTIONS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "numberOfQuestions must be between 1 and " + MAX_QUESTIONS);
        }
        if (req.getDurationMinutes() != null && (req.getDurationMinutes() < 1 || req.getDurationMinutes() > 180)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "durationMinutes must be between 1 and 180");
        }
    }

    private void validateGeneratedResponse(GenerateQuestionsResponse res, int expected) {
        if (res == null || res.getQuestions() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI response is empty");
        }
        if (res.getQuestions().size() != expected) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI must generate exactly " + expected + " questions");
        }
        for (GeneratedQuestionItemResponse item : res.getQuestions()) {
            validateGeneratedItem(item);
        }
    }

    private void validateGeneratedItem(GeneratedQuestionItemResponse item) {
        if (item == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI question is null");
        if (item.getQuestion() == null || item.getQuestion().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI question content is blank");
        if (item.getOptions() == null)
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI options is null");

        for (String k : List.of("A", "B", "C", "D")) {
            String v = item.getOptions().get(k);
            if (v == null || v.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI options must contain A,B,C,D");
            }
        }

        String ca = normalizeChoice(item.getCorrectAnswer());
        if (!isValidChoice(ca)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI correctAnswer must be A/B/C/D");
        }
    }

    private User getMe(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private boolean isValidChoice(String s) {
        return "A".equals(s) || "B".equals(s) || "C".equals(s) || "D".equals(s);
    }

    private String writeOptionsJson(Map<String, String> options) {
        try {
            Map<String, String> ordered = new LinkedHashMap<>();
            ordered.put("A", options.get("A"));
            ordered.put("B", options.get("B"));
            ordered.put("C", options.get("C"));
            ordered.put("D", options.get("D"));
            return om.writeValueAsString(ordered);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cannot serialize options", e);
        }
    }

    private Map<String, String> readOptionsJson(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) return Map.of();
        try {
            return om.readValue(optionsJson, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String writeSelectedAnswersJson(Map<Long, String> selected) {
        try {
            return om.writeValueAsString(selected);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<Long, String> readSelectedAnswersJson(String json) {
        try {
            if (json == null || json.isBlank()) return new HashMap<>();
            return om.readValue(json, new TypeReference<Map<Long, String>>() {
            });
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String normalizeChoice(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private String safe(String s) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() > 140 ? t.substring(0, 140) + "..." : t;
    }

    private String buildFeedbackPrompt(int score, int correct, int total, List<String> wrongSummaries) {
        String wrongBlock = wrongSummaries.isEmpty()
                ? "Không có câu sai."
                : String.join("\n", wrongSummaries);

        return """
                Bạn là trợ lý học tập. Hãy nhận xét ngắn gọn, tích cực và thực tế cho học viên dựa trên kết quả ôn tập.
                
                KẾT QUẢ:
                - Điểm: %d/100
                - Đúng: %d/%d
                - Các câu sai:
                %s
                
                YÊU CẦU:
                - Viết 4-6 câu tiếng Việt
                - Nêu 1-2 điểm mạnh
                - Nêu 1-2 điểm cần cải thiện
                - Gợi ý cách ôn lại (ngắn gọn, cụ thể)
                - Không nhắc đến “AI”, không dài dòng
                """.formatted(score, correct, total, wrongBlock);
    }
}
