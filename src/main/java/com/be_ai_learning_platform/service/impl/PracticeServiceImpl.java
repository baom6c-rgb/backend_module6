package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.ai.GeminiResponsesClient;
import com.be_ai_learning_platform.dto.request.GeneratePracticeSessionRequest;
import com.be_ai_learning_platform.dto.request.PracticeGenerateRequest;
import com.be_ai_learning_platform.dto.request.StartPracticeSessionRequest;
import com.be_ai_learning_platform.dto.request.SubmitPracticeRequest;
import com.be_ai_learning_platform.dto.request.SubmitPracticeSessionRequest;
import com.be_ai_learning_platform.dto.response.AttemptDetailResponse;
import com.be_ai_learning_platform.dto.response.AttemptQuestionResponse;
import com.be_ai_learning_platform.dto.response.AttemptReviewItemResponse;
import com.be_ai_learning_platform.dto.response.AttemptReviewResponse;
import com.be_ai_learning_platform.dto.response.GeneratePracticeSessionResponse;
import com.be_ai_learning_platform.dto.response.GenerateQuestionsResponse;
import com.be_ai_learning_platform.dto.response.GeneratedQuestionItemResponse;
import com.be_ai_learning_platform.dto.response.PracticeQuestionV2Response;
import com.be_ai_learning_platform.dto.response.RetestStatusResponse;
import com.be_ai_learning_platform.dto.response.StartPracticeResponse;
import com.be_ai_learning_platform.dto.response.StartPracticeSessionResponse;
import com.be_ai_learning_platform.dto.response.SubmitPracticeResponse;
import com.be_ai_learning_platform.dto.response.SubmitPracticeV2Response;
import com.be_ai_learning_platform.entity.Exam;
import com.be_ai_learning_platform.entity.ExamAttempt;
import com.be_ai_learning_platform.entity.ExamQuestion;
import com.be_ai_learning_platform.entity.LearningMaterial;
import com.be_ai_learning_platform.entity.Question;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.ExamResult;
import com.be_ai_learning_platform.entity.enums.ExamType;
import com.be_ai_learning_platform.entity.enums.MaterialStatus;
import com.be_ai_learning_platform.entity.enums.QuestionType;
import com.be_ai_learning_platform.repository.ExamAttemptRepository;
import com.be_ai_learning_platform.repository.ExamQuestionRepository;
import com.be_ai_learning_platform.repository.ExamRepository;
import com.be_ai_learning_platform.repository.LearningMaterialRepository;
import com.be_ai_learning_platform.repository.QuestionRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.PracticeService;
import com.be_ai_learning_platform.service.QuestionGenerationService;
import com.be_ai_learning_platform.service.SystemSettingsService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PracticeServiceImpl implements PracticeService {

    private static final int MAX_AI_FEEDBACK_CHARS = 3500;

    // Tổng điểm theo loại câu hỏi
    private static final int TOTAL_SCORE = 100;
    private static final int MCQ_TOTAL_POINTS = 70;
    private static final int ESSAY_TOTAL_POINTS = 30;

    // validate behavior
    private static final int DEFAULT_MAX_QUESTIONS = 20;

    // clamp duration (settings chỉ có minutesPerQuestion)
    private static final int DURATION_MIN_MINUTES = 5;
    private static final int DURATION_MAX_MINUTES = 120;

    // title
    private static final int EXAM_TITLE_MAX_CHARS = 120;

    private final UserRepository userRepo;
    private final LearningMaterialRepository materialRepo;

    private final ExamRepository examRepo;
    private final ExamAttemptRepository attemptRepo;
    private final QuestionRepository questionRepo;
    private final ExamQuestionRepository examQuestionRepo;

    private final QuestionGenerationService questionGenerationService;

    private final GeminiResponsesClient responsesClient;
    private final ObjectMapper om;
    private final Cache<String, Object> practiceSessionCache;

    private final SystemSettingsService settingsService;

    private void ensureAiAvailable() {
        if (!settingsService.isAiEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI hiện tại không thể sử dụng"
            );
        }
    }

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
            Cache<String, Object> practiceSessionCache,
            SystemSettingsService settingsService
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
        this.practiceSessionCache = practiceSessionCache;
        this.settingsService = settingsService;
    }

    // =========================================================
    // V1 - Preview (cache)
    // =========================================================
    @Override
    public GenerateQuestionsResponse generatePreview(String email, PracticeGenerateRequest req) {
        validateGenerateRequest(req);
        ensureAiAvailable();
        ensureValidConfiguredCounts();

        User me = getMe(email);

        LearningMaterial material = materialRepo.findByIdAndUser(req.getMaterialId(), me)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));

        if (material.getStatus() != MaterialStatus.EXTRACTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Material is not extracted yet");
        }

        // Backward compatibility: client gửi token thì thử lấy cache
        String incomingToken = normalizeToken(req.getPreviewToken());
        if (!incomingToken.isBlank()) {
            GenerateQuestionsResponse cached = getCachedPreview(email, incomingToken);
            if (cached != null) return cached;
        }

        int totalQuestions = getConfiguredTotalQuestions();

        // Generate mới (BE ignore req.getNumberOfQuestions, dùng settings)
        GenerateQuestionsResponse generated =
                questionGenerationService.generate(email, req.getMaterialId(), totalQuestions);

        validateGeneratedResponse(generated, totalQuestions);

        // Server tự tạo token cho preview (source of truth)
        String token = UUID.randomUUID().toString();
        generated.setPreviewToken(token);

        // cache theo token mới
        practiceSessionCache.put(previewKey(email, token), generated);

        return generated;
    }

    // =========================================================
    // V1 - Start (save questions to DB)
    // =========================================================
    @Override
    @Transactional
    public StartPracticeResponse start(String email, PracticeGenerateRequest req) {
        validateGenerateRequest(req);
        ensureValidConfiguredCounts();

        User me = getMe(email);

        LearningMaterial material = materialRepo.findByIdAndUser(req.getMaterialId(), me)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));

        if (material.getStatus() != MaterialStatus.EXTRACTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Material is not extracted yet");
        }

        // Strict mode: bắt buộc token để đảm bảo không gọi AI lần 2
        String token = normalizeToken(req.getPreviewToken());
        if (token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "previewToken is required. Please generate preview first.");
        }

        GenerateQuestionsResponse generated = getCachedPreview(email, token);
        if (generated == null) {
            throw new ResponseStatusException(HttpStatus.GONE, "Preview expired. Please generate preview again.");
        }

        int totalQuestions = getConfiguredTotalQuestions();
        validateGeneratedResponse(generated, totalQuestions);

        // dùng xong xoá cache để tránh reuse
        practiceSessionCache.invalidate(previewKey(email, token));

        Exam exam = new Exam();
        exam.setUser(me);
        exam.setType(ExamType.PRACTICE);

        int duration = (req.getDurationMinutes() != null)
                ? req.getDurationMinutes()
                : computeDurationMinutes(totalQuestions);
        exam.setDurationMinutes(duration);

        exam.setPassScore(settingsService.getPassScore());
        exam.setCreatedAt(LocalDateTime.now());

        // ✅ AI đặt tên bài test theo học liệu (fallback nếu AI lỗi)
        exam.setTitle(generateExamTitle(me, material, totalQuestions));

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
                // ESSAY: không lưu sampleAnswer vào correctAnswer. Lưu rubric vào analysis.
                q.setCorrectAnswer(null);
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

    // =========================================================
    // V1 - Attempt detail
    // =========================================================
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

    // =========================================================
    // V1 - Submit (70/30 theo số câu, tổng 100)
    // =========================================================
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

        // Map answer theo questionId
        Map<Long, SubmitPracticeRequest.AnswerItem> answerByQid = req.getAnswers().stream()
                .filter(a -> a.getQuestionId() != null)
                .collect(Collectors.toMap(
                        SubmitPracticeRequest.AnswerItem::getQuestionId,
                        a -> a,
                        (a, b) -> b
                ));

        // Count số câu theo loại
        List<Question> mcqQuestions = examQuestions.stream()
                .filter(q -> q.getQuestionType() == QuestionType.MCQ)
                .toList();
        List<Question> essayQuestions = examQuestions.stream()
                .filter(q -> q.getQuestionType() == QuestionType.ESSAY)
                .toList();

        int mcqCount = mcqQuestions.size();
        int essayCount = essayQuestions.size();

        // Allocate điểm tuyệt đối 70/30, nếu thiếu 1 loại thì loại còn lại ăn 100
        int mcqBudget = (mcqCount > 0 && essayCount > 0) ? MCQ_TOTAL_POINTS : (mcqCount > 0 ? TOTAL_SCORE : 0);
        int essayBudget = (mcqCount > 0 && essayCount > 0) ? ESSAY_TOTAL_POINTS : (essayCount > 0 ? TOTAL_SCORE : 0);

        Map<Long, Integer> mcqMaxPointsByQid = allocatePointsByQuestionId(mcqQuestions, mcqBudget);
        Map<Long, Integer> essayMaxPointsByQid = allocatePointsByQuestionId(essayQuestions, essayBudget);

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

                int maxScore = Math.max(0, mcqMaxPointsByQid.getOrDefault(q.getId(), 0));
                int score = isCorrect ? maxScore : 0;

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
                // ESSAY
                String textAnswer = ans != null ? safeTrim(ans.getTextAnswer(), 5000) : "";
                Rubric rubric = readRubric(q.getAnalysis(), q.getCorrectAnswer());

                int maxScore = Math.max(0, essayMaxPointsByQid.getOrDefault(q.getId(), 0));

                int aiScore10; // 0..10
                String perQuestionFeedback;

                try {
                    AiEssayGrade g = gradeEssayByAi(q, textAnswer, rubric);
                    aiScore10 = g.score; // 0..10
                    perQuestionFeedback = buildEssayFeedbackText(g);
                } catch (Exception aiErr) {
                    // fallback rule-based
                    EssayScore fb = scoreEssay(textAnswer, rubric);
                    aiScore10 = Math.max(0, Math.min(fb.score, 10));
                    perQuestionFeedback = (fb.feedback == null ? "" : fb.feedback) + " (fallback: AI tạm lỗi)";
                }

                // Quy đổi điểm AI (0..10) -> thang điểm maxScore của câu
                int score = (int) Math.round((aiScore10 / 10.0) * maxScore);
                score = Math.max(0, Math.min(score, maxScore));

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

        int pass = exam.getPassScore() != null ? exam.getPassScore() : settingsService.getPassScore();
        attempt.setStatus(scorePct >= pass ? ExamResult.PASSED : ExamResult.FAILED);

        // AI feedback tổng (sau khi grade)
        String aiFeedback = "";
        try {
            String prompt = buildAiFeedbackPrompt(me.getFullName(), examQuestions, results, scorePct);
            aiFeedback = safeTrim(responsesClient.generateText(prompt), MAX_AI_FEEDBACK_CHARS);
        } catch (Exception e) {
            // keep silent, fallback below
        }

        if (aiFeedback == null || aiFeedback.isBlank()) {
            aiFeedback = """
Chào bạn,
Điểm mạnh:
- Bạn đã hoàn thành bài và có nỗ lực trả lời.
- Một số câu làm đúng hướng theo học liệu.
Điểm yếu:
- Một số ý trọng tâm còn thiếu/nhầm.
- Cách trình bày chưa rõ, thiếu keywords quan trọng.
Gợi ý ôn tập:
- Bấm “Xem lại đáp án” để xem câu sai và giải thích chi tiết.
- Ôn lại khái niệm chính và ví dụ trong học liệu.
- Làm lại bài dưới giới hạn thời gian để tăng tốc độ.
""".trim();
        }

        String formatted = formatAiFeedback(me.getFullName(), aiFeedback);
        attempt.setAiFeedback(formatted);

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
        res.setAiFeedback(formatted);
        return res;
    }

    // =========================================================
    // V1 - Review
    // =========================================================
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
                item.setMaxScore(ar != null ? ar.maxScore : 0);
                item.setFeedback(ar != null ? ar.feedback : "");

            } else {
                Rubric rubric = readRubric(q.getAnalysis(), q.getCorrectAnswer());

                item.setOptions(null);
                item.setYourAnswer(ar != null ? ar.textAnswer : "");
                item.setSampleAnswer(rubric.sampleAnswer);

                int score = ar != null ? ar.score : 0;
                int max = ar != null ? ar.maxScore : 0;

                item.setScore(score);
                item.setMaxScore(max);
                item.setIsCorrect(max > 0 && score >= max); // perfect mới coi là "đúng"
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

    // =========================================================
    // V2 - no preview, no DB until submit
    // =========================================================
    @Override
    public GeneratePracticeSessionResponse generateSessionV2(String email, GeneratePracticeSessionRequest req) {
        validateGenerateV2Request(req);
        ensureAiAvailable();
        ensureValidConfiguredCounts();

        User me = getMe(email);

        LearningMaterial material = materialRepo.findByIdAndUser(req.getMaterialId(), me)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));

        if (material.getStatus() != MaterialStatus.EXTRACTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Material is not extracted yet");
        }

        int totalQuestions = getConfiguredTotalQuestions();

        // AI generate 1 lần (BE ignore req.getNumberOfQuestions)
        GenerateQuestionsResponse generated =
                questionGenerationService.generate(email, req.getMaterialId(), totalQuestions);

        validateGeneratedResponse(generated, totalQuestions);

        String token = UUID.randomUUID().toString();
        int duration = computeDurationMinutes(totalQuestions);

        PracticeSessionData session = new PracticeSessionData();
        session.sessionToken = token;
        session.userId = me.getId();
        session.userFullName = safeTrim(me.getFullName(), 120);
        session.materialId = req.getMaterialId();
        session.numberOfQuestions = totalQuestions;
        session.durationMinutes = duration;

        // gắn key ổn định cho từng câu (dùng để submit vì DB chưa có questionId)
        List<SessionQuestion> qs = new ArrayList<>();
        for (GeneratedQuestionItemResponse item : generated.getQuestions()) {
            SessionQuestion sq = new SessionQuestion();
            sq.key = UUID.randomUUID().toString();
            sq.item = item;
            qs.add(sq);
        }
        session.questions = qs;

        practiceSessionCache.put(sessionKey(email, token), session);

        GeneratePracticeSessionResponse res = new GeneratePracticeSessionResponse();
        res.setSessionToken(token);
        res.setMaterialId(req.getMaterialId());
        res.setNumberOfQuestions(totalQuestions);
        res.setDurationMinutes(duration);
        return res;
    }

    @Override
    public StartPracticeSessionResponse startSessionV2(String email, StartPracticeSessionRequest req) {
        if (req == null || req.getSessionToken() == null || req.getSessionToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionToken is required");
        }

        PracticeSessionData session = getSessionOrThrow(email, req.getSessionToken());
        if (session.startedAt == null) {
            session.startedAt = LocalDateTime.now();
            session.deadline = session.startedAt.plusMinutes(session.durationMinutes);
            practiceSessionCache.put(sessionKey(email, session.sessionToken), session);
        }
        return buildSessionResponse(session);
    }

    @Override
    public StartPracticeSessionResponse getSessionV2(String email, String sessionToken) {
        PracticeSessionData session = getSessionOrThrow(email, sessionToken);
        return buildSessionResponse(session);
    }

    @Override
    @Transactional
    public SubmitPracticeV2Response submitSessionV2(String email, String sessionToken, SubmitPracticeSessionRequest req) {
        if (req == null || req.getAnswers() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Answers is required");
        }

        User me = getMe(email);
        PracticeSessionData session = getSessionOrThrow(email, sessionToken);

        if (!Objects.equals(session.userId, me.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session does not belong to current user");
        }
        if (session.startedAt == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is not started yet");
        }
        if (session.submitted) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session already submitted");
        }

        // map answers by key
        Map<String, SubmitPracticeSessionRequest.AnswerItem> ansByKey = req.getAnswers().stream()
                .filter(a -> a.getQuestionKey() != null && !a.getQuestionKey().isBlank())
                .collect(Collectors.toMap(
                        SubmitPracticeSessionRequest.AnswerItem::getQuestionKey,
                        a -> a,
                        (a, b) -> b
                ));

        // tách câu theo loại
        List<SessionQuestion> mcq = session.questions.stream()
                .filter(q -> q.item != null && q.item.getQuestionType() == QuestionType.MCQ)
                .toList();
        List<SessionQuestion> essay = session.questions.stream()
                .filter(q -> q.item != null && q.item.getQuestionType() == QuestionType.ESSAY)
                .toList();

        int mcqCount = mcq.size();
        int essayCount = essay.size();

        int mcqBudget = (mcqCount > 0 && essayCount > 0) ? MCQ_TOTAL_POINTS : (mcqCount > 0 ? TOTAL_SCORE : 0);
        int essayBudget = (mcqCount > 0 && essayCount > 0) ? ESSAY_TOTAL_POINTS : (essayCount > 0 ? TOTAL_SCORE : 0);

        Map<String, Integer> mcqMax = allocatePointsByKey(mcq, mcqBudget);
        Map<String, Integer> essayMax = allocatePointsByKey(essay, essayBudget);

        // chấm điểm (theo key)
        List<AnswerResultV2> resultsV2 = new ArrayList<>();
        int totalEarned = 0;
        int totalMax = 0;

        for (SessionQuestion sq : session.questions) {
            if (sq == null || sq.item == null) continue;
            GeneratedQuestionItemResponse item = sq.item;

            SubmitPracticeSessionRequest.AnswerItem a = ansByKey.get(sq.key);

            if (item.getQuestionType() == QuestionType.MCQ) {
                String sel = a != null ? normalizeChoice(a.getSelectedAnswer()) : "";
                if (!isValidChoice(sel)) sel = "";
                String right = normalizeChoice(item.getCorrectAnswer());
                boolean isCorrect = !sel.isBlank() && sel.equals(right);

                int maxScore = Math.max(0, mcqMax.getOrDefault(sq.key, 0));
                int score = isCorrect ? maxScore : 0;

                resultsV2.add(AnswerResultV2.forMcq(sq.key, sel, right, score, maxScore, isCorrect ? "Đúng" : "Sai"));
                totalEarned += score;
                totalMax += maxScore;

            } else {
                String textAnswer = a != null ? safeTrim(a.getTextAnswer(), 5000) : "";
                Rubric rubric = new Rubric(
                        safeTrim(item.getSampleAnswer(), 2000),
                        item.getKeywords() == null ? List.of() : item.getKeywords(),
                        10
                );

                int maxScore = Math.max(0, essayMax.getOrDefault(sq.key, 0));

                // rule-based keyword coverage -> score 0..10 (KHÔNG MIN=1)
                EssayScore fb = scoreEssay(textAnswer, rubric);
                int score10 = Math.max(0, Math.min(fb.score, 10));

                int score = (int) Math.round((score10 / 10.0) * maxScore);
                score = Math.max(0, Math.min(score, maxScore));

                resultsV2.add(AnswerResultV2.forEssay(sq.key, textAnswer, rubric.sampleAnswer, score, maxScore, fb.feedback));
                totalEarned += score;
                totalMax += maxScore;
            }
        }

        int scorePct = (int) Math.round((totalEarned * 100.0) / Math.max(totalMax, 1));

        // ============ LƯU DB (chỉ ở submit) ============
        LearningMaterial material = materialRepo.findByIdAndUser(session.materialId, me)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));

        Exam exam = new Exam();
        exam.setUser(me);
        exam.setType(ExamType.PRACTICE);
        exam.setDurationMinutes(session.durationMinutes);
        exam.setPassScore(settingsService.getPassScore());
        exam.setCreatedAt(LocalDateTime.now());

        // ✅ AI đặt tên bài test theo học liệu (fallback nếu AI lỗi)
        exam.setTitle(generateExamTitle(me, material, session.numberOfQuestions == null ? 0 : session.numberOfQuestions));

        exam = examRepo.save(exam);

        // persist questions + map key->questionId
        Map<String, Long> keyToQid = new HashMap<>();

        for (SessionQuestion sq : session.questions) {
            GeneratedQuestionItemResponse item = sq.item;
            if (item == null || item.getQuestionType() == null) continue;

            Question q = new Question();
            q.setMaterial(material);
            q.setQuestionType(item.getQuestionType());
            q.setContent(item.getQuestion() == null ? "" : item.getQuestion().trim());

            if (item.getQuestionType() == QuestionType.MCQ) {
                q.setCorrectAnswer(normalizeChoice(item.getCorrectAnswer()));
                q.setOptionsJson(writeOptionsJson(item.getOptions()));
                q.setAnalysis(safeTrim(item.getAnalysis(), 2000));
            } else {
                q.setCorrectAnswer(null);
                q.setOptionsJson(null);
                q.setAnalysis(writeRubricJson(item.getSampleAnswer(), item.getKeywords(), item.getMaxScore()));
            }

            q = questionRepo.save(q);
            keyToQid.put(sq.key, q.getId());

            ExamQuestion eq = new ExamQuestion();
            eq.setExam(exam);
            eq.setQuestion(q);
            examQuestionRepo.save(eq);
        }

        // chuyển resultsV2 -> AnswerResult (questionId)
        List<AnswerResult> persisted = new ArrayList<>();
        for (AnswerResultV2 r : resultsV2) {
            Long qid = keyToQid.get(r.questionKey);
            if (qid == null) continue;
            if (r.questionType == QuestionType.MCQ) {
                persisted.add(AnswerResult.forMcq(qid, r.selectedAnswer, r.correctAnswer, r.score, r.maxScore, r.feedback));
            } else {
                persisted.add(AnswerResult.forEssay(qid, r.textAnswer, r.sampleAnswer, r.score, r.maxScore, r.feedback));
            }
        }

        ExamAttempt attempt = new ExamAttempt();
        attempt.setUser(me);
        attempt.setExam(exam);
        attempt.setClassroom(me.getClassName());
        attempt.setLearningModule(me.getLearningModule());
        attempt.setStartTime(session.startedAt);
        attempt.setSubmitTime(LocalDateTime.now());
        attempt.setAnswersJson(writeAnswersJson(persisted));
        attempt.setScore(scorePct);

        int pass = settingsService.getPassScore();
        attempt.setStatus(scorePct >= pass ? ExamResult.PASSED : ExamResult.FAILED);

        boolean timedOut = session.deadline != null && LocalDateTime.now().isAfter(session.deadline);

        String rawAiFeedback = "";
        try {
            rawAiFeedback = responsesClient.generateText(buildAiFeedbackPromptFromGenerated(session, resultsV2, scorePct));
        } catch (Exception ignore) {
        }

        String formatted = formatAiFeedback(session.userFullName, rawAiFeedback);
        attempt.setAiFeedback(formatted);

        attemptRepo.save(attempt);

        // invalidate session (tránh reuse + tránh rác)
        session.submitted = true;
        practiceSessionCache.invalidate(sessionKey(email, session.sessionToken));

        SubmitPracticeV2Response res = new SubmitPracticeV2Response();
        res.setAttemptId(attempt.getId());
        res.setScore(scorePct);
        res.setEarnedPoints(totalEarned);
        res.setTotalPoints(totalMax);
        res.setStatus(attempt.getStatus());
        res.setTimedOut(timedOut);
        res.setFeedback(buildStaticFeedback(scorePct));
        res.setAiFeedback(formatted);

        // ===== Retest (cooldown) =====
        boolean failed = attempt.getStatus() == ExamResult.FAILED;
        res.setShowRetest(failed);

        if (failed) {
            int cooldownMin = Math.max(0, settingsService.getRetestCooldownMinutes());
            LocalDateTime availableAt = (attempt.getSubmitTime() != null)
                    ? attempt.getSubmitTime().plusMinutes(cooldownMin)
                    : LocalDateTime.now().plusMinutes(cooldownMin);

            long remainingSec = Math.max(0, Duration.between(LocalDateTime.now(), availableAt).getSeconds());

            res.setRetestCooldownMinutes(cooldownMin);
            res.setRetestAvailableAt(availableAt);
            res.setRetestRemainingSeconds(remainingSec);
            res.setCanRetestNow(remainingSec == 0);
        } else {
            res.setRetestCooldownMinutes(0);
            res.setRetestAvailableAt(null);
            res.setRetestRemainingSeconds(0L);
            res.setCanRetestNow(true);
        }

        return res;
    }

    // =========================================================
    // V2 - Retest
    // =========================================================
    @Override
    @Transactional(readOnly = true)
    public RetestStatusResponse getRetestStatusV2(String email, Long attemptId) {
        User me = getMe(email);

        ExamAttempt attempt = attemptRepo.findByIdAndUserIdFetchExam(attemptId, me.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));

        if (attempt.getSubmitTime() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attempt is not submitted yet");
        }

        boolean failed = attempt.getStatus() == ExamResult.FAILED;

        int cooldownMin = Math.max(0, settingsService.getRetestCooldownMinutes());
        LocalDateTime availableAt = attempt.getSubmitTime().plusMinutes(cooldownMin);

        long remainingSec = 0;
        if (failed) {
            remainingSec = Math.max(0, Duration.between(LocalDateTime.now(), availableAt).getSeconds());
        }

        RetestStatusResponse res = new RetestStatusResponse();
        res.setAttemptId(attempt.getId());
        res.setShowRetest(failed);
        res.setCooldownMinutes(cooldownMin);
        res.setAvailableAt(availableAt);
        res.setRemainingSeconds(remainingSec);
        res.setCanRetestNow(failed && remainingSec == 0);
        return res;
    }

    @Override
    @Transactional
    public StartPracticeSessionResponse startRetestV2(String email, Long attemptId) {
        User me = getMe(email);

        ExamAttempt attempt = attemptRepo.findByIdAndUserIdFetchExam(attemptId, me.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));

        if (attempt.getSubmitTime() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attempt is not submitted yet");
        }

        if (attempt.getStatus() != ExamResult.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Retest is only available when FAILED");
        }

        RetestStatusResponse status = getRetestStatusV2(email, attemptId);
        if (!Boolean.TRUE.equals(status.getCanRetestNow())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Retest is not available yet");
        }

        Long examId = attempt.getExam() != null ? attempt.getExam().getId() : null;
        if (examId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attempt has no exam");
        }

        List<ExamQuestion> examQuestions = examQuestionRepo.findAllByExamIdFetchQuestion(examId);
        if (examQuestions == null || examQuestions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No questions found for this attempt");
        }

        Long materialId = getMaterialIdFromExamQuestions(examQuestions);
        if (materialId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot resolve materialId from attempt");
        }

        String focusText = buildWeakAreasText(attempt, examQuestions);

        ensureAiAvailable();
        ensureValidConfiguredCounts();

        int totalQuestions = getConfiguredTotalQuestions();

        GenerateQuestionsResponse generated =
                questionGenerationService.generateRetest(email, materialId, totalQuestions, focusText);

        validateGeneratedResponse(generated, totalQuestions);

        // create & start new session immediately
        String token = UUID.randomUUID().toString();
        int duration = computeDurationMinutes(totalQuestions);

        PracticeSessionData session = new PracticeSessionData();
        session.sessionToken = token;
        session.userId = me.getId();
        session.userFullName = safeTrim(me.getFullName(), 120);
        session.materialId = materialId;
        session.numberOfQuestions = totalQuestions;
        session.durationMinutes = duration;

        List<SessionQuestion> qs = new ArrayList<>();
        for (GeneratedQuestionItemResponse item : generated.getQuestions()) {
            SessionQuestion sq = new SessionQuestion();
            sq.key = UUID.randomUUID().toString();
            sq.item = item;
            qs.add(sq);
        }
        session.questions = qs;

        session.startedAt = LocalDateTime.now();
        session.deadline = session.startedAt.plusMinutes(session.durationMinutes);

        practiceSessionCache.put(sessionKey(email, token), session);

        return buildSessionResponse(session);
    }

    private Long getMaterialIdFromExamQuestions(List<ExamQuestion> examQuestions) {
        for (ExamQuestion eq : examQuestions) {
            if (eq != null && eq.getQuestion() != null && eq.getQuestion().getMaterial() != null) {
                return eq.getQuestion().getMaterial().getId();
            }
        }
        return null;
    }

    private String buildWeakAreasText(ExamAttempt attempt, List<ExamQuestion> examQuestions) {
        List<AnswerResult> stored = readAnswersJson(attempt.getAnswersJson());
        Map<Long, AnswerResult> storedMap = stored.stream()
                .filter(a -> a != null && a.questionId != null)
                .collect(Collectors.toMap(a -> a.questionId, a -> a, (a, b) -> b));

        StringBuilder sb = new StringBuilder();
        sb.append("WEAK AREAS from previous attempt:\n");

        int added = 0;

        for (ExamQuestion eq : examQuestions) {
            if (eq == null || eq.getQuestion() == null) continue;
            Question q = eq.getQuestion();

            AnswerResult ar = storedMap.get(q.getId());
            if (ar == null) continue;

            boolean weak;
            if (ar.questionType == QuestionType.MCQ) {
                weak = (ar.score == null || ar.score <= 0);
            } else {
                int sc = ar.score == null ? 0 : ar.score;
                int mx = ar.maxScore == null ? 0 : ar.maxScore;
                weak = (mx <= 0) ? (sc <= 0) : (sc < Math.ceil(mx * 0.7));
            }

            if (!weak) continue;

            sb.append("- Question: ").append(safeTrim(q.getContent(), 450)).append("\n");

            String analysis = safeTrim(q.getAnalysis(), 450);
            if (analysis != null && !analysis.isBlank()) {
                sb.append("  Analysis/Rubric: ").append(analysis).append("\n");
            }

            if (ar.feedback != null && !ar.feedback.isBlank()) {
                sb.append("  Feedback: ").append(safeTrim(ar.feedback, 300)).append("\n");
            }

            added++;
            if (added >= 12) break;
        }

        if (added == 0) {
            sb.append("(No weak items detected. Focus on core concepts in material.)\n");
        }

        return sb.toString();
    }

    // =========================================================
    // Internal helper models
    // =========================================================
    private static class PracticeSessionData {
        public String sessionToken;
        public Long userId;
        public String userFullName;

        public Long materialId;
        public Integer numberOfQuestions;
        public Integer durationMinutes;

        public LocalDateTime startedAt;
        public LocalDateTime deadline;
        public boolean submitted;

        public List<SessionQuestion> questions = new ArrayList<>();
    }

    private static class SessionQuestion {
        public String key;
        public GeneratedQuestionItemResponse item;
    }

    private static class AnswerResultV2 {
        public String questionKey;
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

        public static AnswerResultV2 forMcq(String key, String selected, String correct, int score, int max, String feedback) {
            AnswerResultV2 r = new AnswerResultV2();
            r.questionKey = key;
            r.questionType = QuestionType.MCQ;
            r.selectedAnswer = selected;
            r.correctAnswer = correct;
            r.score = score;
            r.maxScore = max;
            r.feedback = feedback;
            return r;
        }

        public static AnswerResultV2 forEssay(String key, String textAnswer, String sampleAnswer, int score, int max, String feedback) {
            AnswerResultV2 r = new AnswerResultV2();
            r.questionKey = key;
            r.questionType = QuestionType.ESSAY;
            r.textAnswer = textAnswer;
            r.sampleAnswer = sampleAnswer;
            r.score = score;
            r.maxScore = max;
            r.feedback = feedback;
            return r;
        }
    }

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
        public int score;       // 0..10
        public String feedback;

        public EssayScore(int score, String feedback) {
            this.score = score;
            this.feedback = feedback;
        }
    }

    private static class AiEssayGrade {
        public Integer score;            // 0..10
        public String explanation;       // giải thích
        public List<String> needReview;  // gợi ý ôn
        public AiEssayGrade() {}
    }

    // =========================================================
    // Exam title by AI (fallback safe)
    // =========================================================
    private String generateExamTitle(User me, LearningMaterial material, int numberOfQuestions) {
        String materialName = resolveMaterialName(material);
        String fallback = safeTrim("Practice - " + materialName, EXAM_TITLE_MAX_CHARS);

        try {
            String hint = resolveMaterialHint(material);

            String prompt = """
Bạn là trợ giảng. Hãy đặt 1 tiêu đề ngắn (tối đa 10 từ, <= 120 ký tự) cho một bài luyện tập.
Yêu cầu:
- Tiếng Việt, ngắn gọn, rõ chủ đề.
- Không dùng dấu ngoặc kép.
- Không thêm emoji/ký tự lạ.
- Chỉ trả về đúng 1 dòng tiêu đề.

Thông tin:
- Học viên: %s
- Số câu: %d
- Gợi ý học liệu (trích đoạn): %s
""".formatted(
                    safeTrim(me.getFullName(), 80),
                    Math.max(0, numberOfQuestions),
                    hint
            );

            String raw = responsesClient.generateText(prompt);
            String title = raw == null ? "" : raw.replaceAll("[\\r\\n]+", " ").trim();
            title = safeTrim(title, EXAM_TITLE_MAX_CHARS);

            if (title.isBlank()) return fallback;
            return title;
        } catch (Exception e) {
            return fallback;
        }
    }

    private String resolveMaterialName(LearningMaterial material) {
        if (material == null) return "Material";

        String name = material.getFileName();
        if (name != null && !name.isBlank()) {
            return safeTrim(name, 80);
        }

        return "Material #" + material.getId();
    }

    private String resolveMaterialHint(LearningMaterial material) {
        if (material == null) return "";
        try {
            String extracted = material.getExtractedText();
            if (extracted == null) extracted = "";
            return safeTrim(extracted, 1200);
        } catch (Exception e) {
            return "";
        }
    }

    // =========================================================
    // ESSAY: AI grading helpers
    // =========================================================
    private AiEssayGrade gradeEssayByAi(Question q, String userAnswer, Rubric rubric) {
        String ua = userAnswer == null ? "" : userAnswer.trim();
        if (ua.isBlank()) {
            AiEssayGrade g = new AiEssayGrade();
            g.score = 0;
            g.explanation = "Chưa trả lời.";
            g.needReview = List.of();
            return g;
        }

        String prompt = """
Bạn là giám khảo chấm câu hỏi tự luận ngắn.
Chỉ dựa vào câu hỏi + đáp án mẫu + bài làm học viên. Không bịa thêm kiến thức ngoài phạm vi.
Chấm điểm thang 0-10 (0 là không trả lời / sai hoàn toàn, 10 là rất tốt).

Trả về DUY NHẤT một JSON hợp lệ theo format:
{
  "score": 0,
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
                safeTrim(ua, 5000)
        );

        String raw = responsesClient.generateText(prompt);
        raw = extractJsonObject(raw);

        try {
            AiEssayGrade g = om.readValue(raw, AiEssayGrade.class);

            int score = g.score == null ? 0 : g.score;
            if (score < 0) score = 0;
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

        // remove code fences
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

        // tolerate trailing commas
        t = t.replaceAll(",\\s*([}\\]])", "$1");
        return t;
    }

    // =========================================================
    // Store rubric as JSON in Question.analysis for ESSAY
    // =========================================================
    private String writeRubricJson(String sampleAnswer, List<String> keywords, Integer maxScoreIgnored) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sampleAnswer", safeTrim(sampleAnswer, 2000));
        data.put("keywords", keywords == null ? List.of() : keywords);
        // maxScore trong rubric giữ 10 (scale AI), điểm thật chia theo số câu sẽ tính ở submit()
        data.put("maxScore", 10);

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

    // fallback rule-based (trả score 0..10)
    private EssayScore scoreEssay(String answer, Rubric rubric) {
        String a = answer == null ? "" : answer.trim();
        if (a.isBlank()) {
            return new EssayScore(0, "Chưa trả lời.");
        }

        List<String> kws = rubric.keywords == null ? List.of() : rubric.keywords;
        if (kws.isEmpty()) {
            return new EssayScore(5, "Có trả lời nhưng rubric chưa đủ rõ, hệ thống chấm tạm theo mức trung bình.");
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
        if (ratio < 0.2) {
            return new EssayScore(0, "Chưa đúng ý trọng tâm.");
        }

        int score10 = (int) Math.round(ratio * 10);

        String fb = missing.isEmpty()
                ? "Tốt ✅ Đủ ý chính theo rubric."
                : "Thiếu ý: " + String.join(", ", missing);

        return new EssayScore(Math.max(0, Math.min(score10, 10)), fb);
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

    // =========================================================
    // AI feedback prompt builders
    // =========================================================
    private String buildAiFeedbackPrompt(String userFullName, List<Question> questions, List<AnswerResult> results, int scorePct) {
        Map<Long, AnswerResult> map = results.stream()
                .collect(Collectors.toMap(a -> a.questionId, a -> a, (a, b) -> b));

        String name = (userFullName == null || userFullName.isBlank()) ? "bạn" : userFullName.trim();

        StringBuilder sb = new StringBuilder();
        sb.append("""
Bạn là trợ giảng. Hãy nhận xét bài làm của học viên bằng tiếng Việt.
YÊU CẦU QUAN TRỌNG:
- BẮT ĐẦU bằng đúng 1 câu chào: "Chào %s,"
- Sau đó chỉ trả về đúng 3 mục sau theo format và KHÔNG thêm mục khác:

Điểm mạnh:
- ...
- ...

Điểm yếu:
- ...
- ...

Gợi ý ôn tập:
- ...
- ...

QUY TẮC:
- Chỉ dùng gạch đầu dòng bắt đầu bằng "- " trong từng mục.
- Không markdown, không in đậm, không đánh số.
- Ngắn gọn nhưng rõ ràng. Không bịa kiến thức ngoài phạm vi câu hỏi.
""".formatted(name));

        sb.append("\nĐiểm tổng: ").append(scorePct).append("/100\n\n");

        int idx = 1;
        for (Question q : questions) {
            AnswerResult ar = map.get(q.getId());

            sb.append("Câu ").append(idx++).append(" (").append(q.getQuestionType()).append("): ")
                    .append(safeTrim(q.getContent(), 800)).append("\n");

            if (q.getQuestionType() == QuestionType.MCQ) {
                sb.append("Đúng: ").append(normalizeChoice(q.getCorrectAnswer()))
                        .append(". Chọn: ").append(ar != null ? safeStr(ar.selectedAnswer) : "")
                        .append(".\n");
            } else {
                Rubric rubric = readRubric(q.getAnalysis(), q.getCorrectAnswer());
                sb.append("Đáp án mẫu: ").append(safeTrim(rubric.sampleAnswer, 800)).append("\n");
                sb.append("Trả lời: ").append(ar != null ? safeTrim(ar.textAnswer, 800) : "").append("\n");
                if (rubric.keywords != null && !rubric.keywords.isEmpty()) {
                    sb.append("Keywords: ").append(String.join(", ", rubric.keywords)).append("\n");
                }
            }

            sb.append("Chấm: ").append(ar != null ? ar.score : 0)
                    .append("/").append(ar != null ? ar.maxScore : 0)
                    .append("\n\n");
        }

        return sb.toString();
    }

    private String buildAiFeedbackPromptFromGenerated(PracticeSessionData session, List<AnswerResultV2> results, int scorePct) {
        Map<String, AnswerResultV2> map = results.stream()
                .collect(Collectors.toMap(a -> a.questionKey, a -> a, (a, b) -> b));

        String name = (session.userFullName == null || session.userFullName.isBlank())
                ? "bạn"
                : session.userFullName.trim();

        StringBuilder sb = new StringBuilder();
        sb.append("""
Bạn là trợ giảng. Hãy nhận xét bài làm của học viên bằng tiếng Việt.
Yêu cầu:
- BẮT ĐẦU bằng đúng 1 câu chào: "Chào %s,"
- Sau đó chỉ trả về đúng 3 mục: Điểm mạnh / Điểm yếu / Gợi ý ôn tập
- Mỗi mục dùng bullet "- "
""".formatted(name));

        sb.append("\nĐiểm tổng: ").append(scorePct).append("/100\n\n");

        int idx = 1;
        for (SessionQuestion sq : session.questions) {
            if (sq == null || sq.item == null) continue;
            AnswerResultV2 ar = map.get(sq.key);

            sb.append("Câu ").append(idx++).append(": ")
                    .append(safeTrim(sq.item.getQuestion(), 800))
                    .append("\n");

            if (sq.item.getQuestionType() == QuestionType.MCQ) {
                sb.append("Đúng: ").append(normalizeChoice(sq.item.getCorrectAnswer()))
                        .append(". Chọn: ").append(ar != null ? safeStr(ar.selectedAnswer) : "")
                        .append(".\n");
            } else {
                sb.append("Đáp án mẫu: ").append(safeTrim(sq.item.getSampleAnswer(), 800)).append("\n");
                sb.append("Trả lời: ").append(ar != null ? safeTrim(ar.textAnswer, 800) : "").append("\n");
                if (sq.item.getKeywords() != null && !sq.item.getKeywords().isEmpty()) {
                    sb.append("Keywords: ").append(String.join(", ", sq.item.getKeywords())).append("\n");
                }
            }

            sb.append("Chấm: ").append(ar != null ? ar.score : 0)
                    .append("/").append(ar != null ? ar.maxScore : 0)
                    .append("\n\n");
        }

        return sb.toString();
    }

    private String formatAiFeedback(String userFullName, String raw) {
        String name = (userFullName == null || userFullName.isBlank()) ? "bạn" : userFullName.trim();
        String text = raw == null ? "" : raw.trim();

        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "");
            text = text.replaceFirst("\\s*```\\s*$", "");
            text = text.trim();
        }

        text = text.replace("**", "")
                .replace("__", "")
                .replace("##", "")
                .replace("###", "")
                .trim();

        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            String l = line.trim();
            if (l.isBlank()) continue;

            if (l.startsWith("•")) l = l.replaceFirst("^•\\s*", "- ");
            if (l.startsWith("*")) l = l.replaceFirst("^\\*\\s*", "- ");
            if (l.startsWith("–")) l = l.replaceFirst("^–\\s*", "- ");
            if (l.startsWith("—")) l = l.replaceFirst("^—\\s*", "- ");
            if (l.matches("^\\-\\s*.+") && !l.startsWith("- ")) {
                l = l.replaceFirst("^\\-\\s*", "- ");
            }

            lines.add(l);
        }

        if (lines.isEmpty()) {
            return """
Chào %s,
Điểm mạnh:
- Bạn đã hoàn thành bài và có nỗ lực trả lời.
- Một số ý trả lời đúng hướng theo học liệu.
Điểm yếu:
- Còn thiếu/nhầm ở các ý trọng tâm trong một số câu.
- Trình bày chưa đủ rõ, thiếu keywords quan trọng.
Gợi ý ôn tập:
- Xem lại các câu sai trong phần “Xem lại đáp án”.
- Ôn lại khái niệm chính và ví dụ trong học liệu.
- Làm lại bài dưới giới hạn thời gian để tăng tốc độ.
""".formatted(name).trim();
        }

        String first = lines.get(0).trim();
        boolean hasGreeting = first.toLowerCase(Locale.ROOT).startsWith("chào ");

        String out = String.join("\n", lines).trim();
        if (!hasGreeting) {
            out = ("Chào " + name + ",\n" + out).trim();
        }

        if (out.length() > MAX_AI_FEEDBACK_CHARS) {
            out = out.substring(0, MAX_AI_FEEDBACK_CHARS);
        }
        return out;
    }

    // =========================================================
    // Helpers
    // =========================================================
    private void validateGenerateRequest(PracticeGenerateRequest req) {
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is required");
        if (req.getMaterialId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "materialId is required");

        // Backward-compatible: FE vẫn gửi numberOfQuestions, nhưng BE ignore (không validate range nữa)
        if (req.getNumberOfQuestions() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "numberOfQuestions is required");
        }
    }

    private void validateGenerateV2Request(GeneratePracticeSessionRequest req) {
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is required");
        if (req.getMaterialId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "materialId is required");

        if (req.getNumberOfQuestions() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "numberOfQuestions is required");
        }
    }

    private int computeDurationMinutes(int numberOfQuestions) {
        int n = Math.max(1, numberOfQuestions);

        double perQ = settingsService.getMinutesPerQuestion();
        if (perQ <= 0) perQ = 2.0;

        int raw = (int) Math.round(n * perQ);

        raw = Math.max(DURATION_MIN_MINUTES, raw);
        raw = Math.min(DURATION_MAX_MINUTES, raw);

        return raw;
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

    private String sessionKey(String email, String token) {
        return "practice_v2:" + email + ":" + token;
    }

    private PracticeSessionData getSessionOrThrow(String email, String sessionToken) {
        String token = normalizeToken(sessionToken);
        if (token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionToken is required");
        }
        Object cached = practiceSessionCache.getIfPresent(sessionKey(email, token));
        if (cached instanceof PracticeSessionData s) {
            return s;
        }
        throw new ResponseStatusException(HttpStatus.GONE, "Session expired. Please generate again.");
    }

    private StartPracticeSessionResponse buildSessionResponse(PracticeSessionData session) {
        StartPracticeSessionResponse res = new StartPracticeSessionResponse();
        res.setSessionToken(session.sessionToken);
        res.setDurationMinutes(session.durationMinutes);
        res.setStartedAt(session.startedAt);
        res.setDeadline(session.deadline);

        List<PracticeQuestionV2Response> questions = new ArrayList<>();
        for (SessionQuestion sq : session.questions) {
            if (sq == null || sq.item == null) continue;

            PracticeQuestionV2Response q = new PracticeQuestionV2Response();
            q.setQuestionKey(sq.key);
            q.setQuestionType(sq.item.getQuestionType());
            q.setContent(safeTrim(sq.item.getQuestion(), 4000));
            q.setOptions(sq.item.getQuestionType() == QuestionType.MCQ ? sq.item.getOptions() : null);
            questions.add(q);
        }
        res.setQuestions(questions);
        return res;
    }

    private GenerateQuestionsResponse getCachedPreview(String email, String token) {
        Object cached = practiceSessionCache.getIfPresent(previewKey(email, token));
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

    private Map<Long, Integer> allocatePointsByQuestionId(List<Question> questions, int totalPoints) {
        Map<Long, Integer> map = new HashMap<>();
        if (questions == null || questions.isEmpty() || totalPoints <= 0) return map;

        int count = questions.size();
        int base = totalPoints / count;
        int rem = totalPoints % count;

        for (int i = 0; i < count; i++) {
            int pts = base + (i < rem ? 1 : 0);
            map.put(questions.get(i).getId(), pts);
        }
        return map;
    }

    private Map<String, Integer> allocatePointsByKey(List<SessionQuestion> questions, int totalPoints) {
        Map<String, Integer> map = new HashMap<>();
        if (questions == null || questions.isEmpty() || totalPoints <= 0) return map;

        int count = questions.size();
        int base = totalPoints / count;
        int rem = totalPoints % count;

        for (int i = 0; i < count; i++) {
            int pts = base + (i < rem ? 1 : 0);
            SessionQuestion q = questions.get(i);
            if (q != null && q.key != null) {
                map.put(q.key, pts);
            }
        }
        return map;
    }

    // =========================================================
    // NEW: Read configured distribution from SystemSettings
    // =========================================================
    private int getConfiguredTotalQuestions() {
        int mcq = Math.max(0, settingsService.getMcqQuestionCount());
        int essay = Math.max(0, settingsService.getEssayQuestionCount());
        int total = mcq + essay;

        if (total <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "System settings invalid: totalQuestions must be > 0");
        }
        return total;
    }

    private void ensureValidConfiguredCounts() {
        int total = getConfiguredTotalQuestions();
        if (total > DEFAULT_MAX_QUESTIONS) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "System settings invalid: totalQuestions must be <= " + DEFAULT_MAX_QUESTIONS
            );
        }
    }
}
