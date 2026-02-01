package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.ai.GeminiResponsesClient;
import com.be_ai_learning_platform.dto.request.*;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PracticeServiceImpl implements PracticeService {

    private static final int MAX_AI_FEEDBACK_CHARS = 3500;

    // ✅ Tổng điểm theo loại câu hỏi
    private static final int TOTAL_SCORE = 100;
    private static final int MCQ_TOTAL_POINTS = 70;
    private static final int ESSAY_TOTAL_POINTS = 30;

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

    // ===== configurable (application.properties) =====
    private final int passScore;
    private final int maxQuestions;

    private final int durationBaseMinutes;
    private final int durationPerQuestionMinutes;
    private final int durationMinMinutes;
    private final int durationMaxMinutes;

    private final int feedbackMaxBullets;

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
            @Value("${ai.practice.pass-score:80}") int passScore,
            @Value("${ai.practice.max-questions:20}") int maxQuestions,
            @Value("${ai.practice.duration.base-minutes:3}") int durationBaseMinutes,
            @Value("${ai.practice.duration.per-question-minutes:2}") int durationPerQuestionMinutes,
            @Value("${ai.practice.duration.min-minutes:10}") int durationMinMinutes,
            @Value("${ai.practice.duration.max-minutes:45}") int durationMaxMinutes,
            @Value("${ai.practice.feedback.max-bullets:8}") int feedbackMaxBullets
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

        this.passScore = passScore;
        this.maxQuestions = maxQuestions;
        this.durationBaseMinutes = durationBaseMinutes;
        this.durationPerQuestionMinutes = durationPerQuestionMinutes;
        this.durationMinMinutes = durationMinMinutes;
        this.durationMaxMinutes = durationMaxMinutes;
        this.feedbackMaxBullets = feedbackMaxBullets;
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
        practiceSessionCache.put(previewKey(email, token), generated);

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
        practiceSessionCache.invalidate(previewKey(email, token));

        Exam exam = new Exam();
        exam.setUser(me);
        exam.setType(ExamType.PRACTICE);
        exam.setDurationMinutes(req.getDurationMinutes() != null ? req.getDurationMinutes() : computeDurationMinutes(req.getNumberOfQuestions()));
        exam.setPassScore(passScore);
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
                // ESSAY: không lưu sampleAnswer vào correctAnswer (tránh lỗi truncate). Lưu rubric vào analysis.
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
    // Submit: tổng điểm = 100, chia 70 MCQ / 30 ESSAY theo SỐ CÂU
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

        // ✅ Map answer theo questionId
        Map<Long, SubmitPracticeRequest.AnswerItem> answerByQid = req.getAnswers().stream()
                .filter(a -> a.getQuestionId() != null)
                .collect(Collectors.toMap(
                        SubmitPracticeRequest.AnswerItem::getQuestionId,
                        a -> a,
                        (a, b) -> b
                ));

        // ✅ Count số câu theo loại
        List<Question> mcqQuestions = examQuestions.stream()
                .filter(q -> q.getQuestionType() == QuestionType.MCQ)
                .toList();
        List<Question> essayQuestions = examQuestions.stream()
                .filter(q -> q.getQuestionType() == QuestionType.ESSAY)
                .toList();

        int mcqCount = mcqQuestions.size();
        int essayCount = essayQuestions.size();

        // ✅ Allocate điểm nguyên theo số câu để tổng đúng 70/30 tuyệt đối
        // Nếu thiếu 1 loại: loại còn lại ăn full 100
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

                // ✅ Quy đổi điểm AI (0..10) -> thang điểm ESSAY của câu (chia theo số câu)
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

        int pass = exam.getPassScore() != null ? exam.getPassScore() : passScore;
        attempt.setStatus(scorePct >= pass ? ExamResult.PASSED : ExamResult.FAILED);

        // AI feedback tổng (sau khi grade)
        String aiFeedback = "";
        try {
            String prompt = buildAiFeedbackPrompt(examQuestions, results, scorePct);
            aiFeedback = safeTrim(responsesClient.generateText(prompt), MAX_AI_FEEDBACK_CHARS);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (aiFeedback == null || aiFeedback.isBlank()) {
            aiFeedback = """
AI feedback tạm thời chưa sẵn sàng (có thể do quota/timeout).
Bạn có thể bấm “Xem lại đáp án” để xem nhận xét chi tiết từng câu (đúng/sai + giải thích + gợi ý ôn lại).
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
                item.setIsCorrect(max > 0 && score >= max); // perfect mới "đúng"
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
    // V2 - no preview, no DB until submit
    // =========================

    @Override
    public GeneratePracticeSessionResponse generateSessionV2(String email, GeneratePracticeSessionRequest req) {
        validateGenerateV2Request(req);

        User me = getMe(email);

        LearningMaterial material = materialRepo.findByIdAndUser(req.getMaterialId(), me)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));

        if (material.getStatus() != MaterialStatus.EXTRACTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Material is not extracted yet");
        }

        // AI generate 1 lần
        GenerateQuestionsResponse generated =
                questionGenerationService.generate(email, req.getMaterialId(), req.getNumberOfQuestions());

        validateGeneratedResponse(generated, req.getNumberOfQuestions());

        String token = UUID.randomUUID().toString();
        int duration = computeDurationMinutes(req.getNumberOfQuestions());

        PracticeSessionData session = new PracticeSessionData();
        session.sessionToken = token;
        session.userId = me.getId();
        session.userFullName = safeTrim(me.getFullName(), 120);
        session.materialId = req.getMaterialId();
        session.numberOfQuestions = req.getNumberOfQuestions();
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
        res.setNumberOfQuestions(req.getNumberOfQuestions());
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

                resultsV2.add(AnswerResultV2.forMcq(sq.key, sel, right, score, maxScore,
                        isCorrect ? "Đúng" : "Sai"));
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
        exam.setPassScore(passScore);
        exam.setCreatedAt(LocalDateTime.now());
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
        attempt.setStatus(scorePct >= passScore ? ExamResult.PASSED : ExamResult.FAILED);

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
        return res;
    }

    // =========================
    // Internal helper models
    // =========================

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
        public int score;
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

    // =========================
    // ESSAY: AI grading helpers
    // =========================
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
            int s = 5;
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
        if (ratio < 0.2) {
            return new EssayScore(0, "Chưa đúng ý trọng tâm.");
        }
        int score10 = (int) Math.round(ratio * 10);

        String fb;
        if (missing.isEmpty()) fb = "Tốt ✅ Đủ ý chính theo rubric.";
        else fb = "Thiếu ý: " + String.join(", ", missing);

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
                    .append(ar != null ? ar.maxScore : 0)
                    .append("\n\n");
        }
        return sb.toString();
    }

    private String buildAiFeedbackPromptFromGenerated(PracticeSessionData session, List<AnswerResultV2> results, int scorePct) {
        Map<String, AnswerResultV2> map = results.stream()
                .collect(Collectors.toMap(a -> a.questionKey, a -> a, (a, b) -> b));

        String name = session.userFullName == null || session.userFullName.isBlank() ? "bạn" : session.userFullName;

        StringBuilder sb = new StringBuilder();
        sb.append("Hãy nhận xét NGẮN GỌN cho học viên (tiếng Việt).\n");
        sb.append("Yêu cầu định dạng: chỉ dùng gạch đầu dòng bắt đầu bằng '- '. Không in đậm, không đánh số, không markdown.\n");
        sb.append("Chào ").append(name).append(".\n");
        sb.append("Điểm tổng: ").append(scorePct).append("/100\n\n");
        sb.append("Nội dung cần có:\n");
        sb.append("- Tổng quan (2-3 ý)\n");
        sb.append("- Lỗi sai nổi bật (tối đa 4 ý)\n");
        sb.append("- Gợi ý ôn tập (3-5 ý)\n\n");

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

    /**
     * Format feedback:
     * - greeting: "Chào <tên user>"
     * - bullet list '- '
     * - remove markdown bold/numbered headings
     */
    private String formatAiFeedback(String userFullName, String raw) {
        String name = (userFullName == null || userFullName.isBlank()) ? "bạn" : userFullName.trim();
        String text = raw == null ? "" : raw;

        // strip common markdown
        text = text.replace("**", "")
                .replace("__", "")
                .replace("##", "")
                .replace("###", "")
                .trim();

        // collect candidate lines
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            String l = line.trim();
            if (l.isBlank()) continue;

            // ✅ FIX: escape đúng trong Java string regex
            // remove leading numbering like "1." "2)" etc
            l = l.replaceFirst("^[0-9]+[\\.)]\\s*", "");

            // ✅ FIX: escape '-' trong character class
            // normalize bullets
            l = l.replaceFirst("^[•\\-–—]+\\s*", "");

            if (l.isBlank()) continue;
            lines.add(l);
        }

        // fallback if AI empty
        if (lines.isEmpty()) {
            lines = List.of(
                    "Bạn làm xong bài, hãy xem lại các câu sai để rút kinh nghiệm.",
                    "Ưu tiên ôn lại phần kiến thức nền và ví dụ trong học liệu.",
                    "Làm lại bài với thời gian giới hạn để tăng tốc độ."
            );
        }

        // keep only top bullets
        int limit = Math.max(3, feedbackMaxBullets);
        if (lines.size() > limit) {
            lines = lines.subList(0, limit);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Chào ").append(name).append(",\n");
        for (String l : lines) {
            sb.append("- ").append(l).append("\n");
        }
        return sb.toString().trim();
    }

    // =========================
    // Helpers
    // =========================
    private void validateGenerateRequest(PracticeGenerateRequest req) {
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is required");
        if (req.getMaterialId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "materialId is required");
        if (req.getNumberOfQuestions() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "numberOfQuestions is required");

        int n = req.getNumberOfQuestions();
        if (n <= 0 || n > maxQuestions) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "numberOfQuestions must be 1.." + maxQuestions);
        }
    }

    private void validateGenerateV2Request(GeneratePracticeSessionRequest req) {
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is required");
        if (req.getMaterialId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "materialId is required");
        if (req.getNumberOfQuestions() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "numberOfQuestions is required");
        int n = req.getNumberOfQuestions();
        if (n <= 0 || n > maxQuestions) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "numberOfQuestions must be 1.." + maxQuestions);
        }
    }

    private int computeDurationMinutes(int numberOfQuestions) {
        int n = Math.max(1, numberOfQuestions);
        int raw = durationBaseMinutes + (n * durationPerQuestionMinutes);
        raw = Math.max(durationMinMinutes, raw);
        raw = Math.min(durationMaxMinutes, raw);
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

    /**
     * ✅ Chia điểm nguyên theo số câu, đảm bảo tổng đúng tuyệt đối.
     * Ví dụ total=70, count=6 -> [12,12,12,12,11,11] (tổng=70)
     */
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

    /**
     * V2: chia điểm theo key (DB chưa có questionId ở thời điểm làm bài).
     */
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
}
