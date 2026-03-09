package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.request.SubmitPracticeRequest;
import com.be_ai_learning_platform.dto.response.AttemptQuestionResponse;
import com.be_ai_learning_platform.dto.response.AttemptReviewItemResponse;
import com.be_ai_learning_platform.dto.response.AttemptReviewResponse;
import com.be_ai_learning_platform.dto.response.StudentAssignedExamItemResponse;
import com.be_ai_learning_platform.dto.response.StudentStartAssignedExamResponse;
import com.be_ai_learning_platform.dto.response.SubmitPracticeResponse;
import com.be_ai_learning_platform.entity.ExamAssignment;
import com.be_ai_learning_platform.entity.ExamAttempt;
import com.be_ai_learning_platform.entity.ExamQuestion;
import com.be_ai_learning_platform.entity.Question;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.AssignmentStatus;
import com.be_ai_learning_platform.entity.enums.ExamResult;
import com.be_ai_learning_platform.entity.enums.ExamType;
import com.be_ai_learning_platform.entity.enums.QuestionType;
import com.be_ai_learning_platform.repository.ExamAssignmentRepository;
import com.be_ai_learning_platform.repository.ExamAttemptRepository;
import com.be_ai_learning_platform.repository.ExamQuestionRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.AiPracticeFeedbackService;
import com.be_ai_learning_platform.service.AiStudyGuideService;
import com.be_ai_learning_platform.service.StudentAssignedExamService;
import com.be_ai_learning_platform.service.SystemSettingsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StudentAssignedExamServiceImpl implements StudentAssignedExamService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    // Chấm điểm giống PracticeServiceImpl
    private static final int TOTAL_SCORE = 100;
    private static final int MCQ_TOTAL_POINTS = 70;
    private static final int ESSAY_TOTAL_POINTS = 30;

    private final UserRepository userRepo;
    private final ExamAssignmentRepository assignmentRepo;
    private final ExamQuestionRepository examQuestionRepo;
    private final ExamAttemptRepository attemptRepo;
    private final SystemSettingsService settingsService;
    private final ObjectMapper om;
    private final AiPracticeFeedbackService aiPracticeFeedbackService;
    private final AiStudyGuideService aiStudyGuideService;

    public StudentAssignedExamServiceImpl(
            UserRepository userRepo,
            ExamAssignmentRepository assignmentRepo,
            ExamQuestionRepository examQuestionRepo,
            ExamAttemptRepository attemptRepo,
            SystemSettingsService settingsService,
            ObjectMapper om,
            AiPracticeFeedbackService aiPracticeFeedbackService,
            AiStudyGuideService aiStudyGuideService
    ) {
        this.userRepo = userRepo;
        this.assignmentRepo = assignmentRepo;
        this.examQuestionRepo = examQuestionRepo;
        this.attemptRepo = attemptRepo;
        this.settingsService = settingsService;
        this.om = om;
        this.aiPracticeFeedbackService = aiPracticeFeedbackService;
        this.aiStudyGuideService = aiStudyGuideService;
    }

    private User requireMe(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private LocalDateTime nowVn() {
        return LocalDateTime.now(VN_ZONE).withNano(0);
    }

    // =========================================================
    // LIST
    // =========================================================
    @Override
    public List<StudentAssignedExamItemResponse> list(String email) {
        User me = requireMe(email);

        List<ExamAssignment> list = assignmentRepo.findAllByStudentFetchExam(me.getId());

        return list.stream()
                .filter(a -> a.getExam() != null && a.getExam().getType() == ExamType.ADMIN_ASSIGNED)
                .map(a -> {
                    StudentAssignedExamItemResponse r = new StudentAssignedExamItemResponse();
                    r.setAssignmentId(a.getId());
                    r.setExamId(a.getExam().getId());
                    r.setExamTitle(a.getExam().getTitle());
                    r.setDurationMinutes(effectiveDuration(a));
                    r.setPassScore(a.getExam().getPassScore());
                    r.setStatus(a.getStatus());

                    r.setOpenAt(a.getOpenAt());
                    r.setDueAt(a.getDueAt());
                    r.setStartedAt(a.getStartedAt());
                    r.setSubmittedAt(a.getSubmittedAt());

                    r.setAttemptId(a.getAttempt() == null ? null : a.getAttempt().getId());
                    return r;
                })
                .collect(Collectors.toList());
    }

    // =========================================================
    // START
    // =========================================================
    @Override
    @Transactional
    public StudentStartAssignedExamResponse start(String email, Long assignmentId) {
        User me = requireMe(email);

        if (assignmentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assignmentId is required");
        }

        ExamAssignment asg = assignmentRepo.findByIdAndStudentId(assignmentId, me.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));

        if (asg.getExam() == null || asg.getExam().getType() != ExamType.ADMIN_ASSIGNED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid assignment");
        }

        LocalDateTime now = nowVn();

        if (asg.getOpenAt() != null && now.isBefore(asg.getOpenAt())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bài kiểm tra chưa mở");
        }
        if (asg.getDueAt() != null && now.isAfter(asg.getDueAt())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bài kiểm tra đã quá hạn");
        }

        if (asg.getStatus() == AssignmentStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đã nộp bài. Không thể làm lại.");
        }

        ExamAttempt attempt;
        if (asg.getAttempt() != null) {
            attempt = asg.getAttempt();
        } else {
            attempt = new ExamAttempt();
            attempt.setUser(me);
            attempt.setExam(asg.getExam());
            attempt.setClassroom(me.getClassName());
            attempt.setLearningModule(me.getLearningModule());
            attempt.setStartTime(now);
            attempt.setStatus(ExamResult.IN_PROGRESS);
            attempt = attemptRepo.save(attempt);

            asg.setAttempt(attempt);
            asg.setStatus(AssignmentStatus.STARTED);
            asg.setStartedAt(now);
        }

        List<ExamQuestion> eqs = examQuestionRepo.findAllByExamIdFetchQuestion(asg.getExam().getId());
        if (eqs == null || eqs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bài kiểm tra chưa có câu hỏi");
        }

        List<AttemptQuestionResponse> questions = eqs.stream()
                .map(eq -> {
                    Question q = eq.getQuestion();
                    if (q == null) {
                        return null;
                    }

                    AttemptQuestionResponse r = new AttemptQuestionResponse();
                    r.setQuestionId(q.getId());
                    r.setQuestionType(q.getQuestionType());
                    r.setContent(q.getContent());

                    if (q.getQuestionType() == QuestionType.MCQ) {
                        r.setOptions(parseOptionsMap(q.getOptionsJson()));
                    } else {
                        r.setOptions(null);
                    }
                    return r;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        StudentStartAssignedExamResponse resp = new StudentStartAssignedExamResponse();
        resp.setAssignmentId(asg.getId());
        resp.setAttemptId(attempt.getId());
        resp.setExamId(asg.getExam().getId());
        resp.setExamTitle(asg.getExam().getTitle());
        resp.setDurationMinutes(effectiveDuration(asg));
        resp.setPassScore(asg.getExam().getPassScore());
        resp.setStartTime(attempt.getStartTime());
        resp.setDeadline(attempt.getStartTime().plusMinutes(resp.getDurationMinutes()));
        resp.setQuestions(questions);
        return resp;
    }

    // =========================================================
    // SUBMIT - chấm giống PracticeServiceImpl (70/30)
    // =========================================================
    @Override
    @Transactional
    public SubmitPracticeResponse submit(String email, Long assignmentId, SubmitPracticeRequest req) {
        User me = requireMe(email);

        if (assignmentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assignmentId is required");
        }

        ExamAssignment asg = assignmentRepo.findByIdAndStudentId(assignmentId, me.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));

        if (asg.getAttempt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn chưa bắt đầu bài kiểm tra");
        }
        if (asg.getStatus() == AssignmentStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đã nộp bài");
        }

        ExamAttempt attempt = attemptRepo.findByIdAndUserId(asg.getAttempt().getId(), me.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));

        int duration = effectiveDuration(asg);
        LocalDateTime now = nowVn();

        boolean timedOut = attempt.getStartTime() != null
                && now.isAfter(attempt.getStartTime().plusMinutes(duration));

        if (asg.getDueAt() != null && now.isAfter(asg.getDueAt())) {
            timedOut = true;
        }

        List<ExamQuestion> eqs = examQuestionRepo.findAllByExamIdFetchQuestion(asg.getExam().getId());
        if (eqs == null || eqs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exam has no questions");
        }

        Map<Long, SubmitPracticeRequest.AnswerItem> answerMap = new HashMap<>();
        if (req != null && req.getAnswers() != null) {
            for (SubmitPracticeRequest.AnswerItem a : req.getAnswers()) {
                if (a == null || a.getQuestionId() == null) {
                    continue;
                }
                answerMap.put(a.getQuestionId(), a);
            }
        }

        List<Question> questions = eqs.stream()
                .map(ExamQuestion::getQuestion)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<Question> mcqQuestions = questions.stream()
                .filter(q -> q.getQuestionType() == QuestionType.MCQ)
                .collect(Collectors.toList());

        List<Question> essayQuestions = questions.stream()
                .filter(q -> isEssayLikeType(q.getQuestionType()))
                .collect(Collectors.toList());

        int mcqCount = mcqQuestions.size();
        int essayCount = essayQuestions.size();

        int mcqBudget = (mcqCount > 0 && essayCount > 0)
                ? MCQ_TOTAL_POINTS
                : (mcqCount > 0 ? TOTAL_SCORE : 0);

        int essayBudget = (mcqCount > 0 && essayCount > 0)
                ? ESSAY_TOTAL_POINTS
                : (essayCount > 0 ? TOTAL_SCORE : 0);

        Map<Long, Integer> mcqMaxPointsByQid = allocatePointsByQuestionId(mcqQuestions, mcqBudget);
        Map<Long, Integer> essayMaxPointsByQid = allocatePointsByQuestionId(essayQuestions, essayBudget);

        int earnedPoints = 0;
        int totalPoints = 0;

        List<Map<String, Object>> storedAnswers = new ArrayList<>();
        List<AttemptReviewItemResponse> reviewItems = new ArrayList<>();

        for (Question q : questions) {
            SubmitPracticeRequest.AnswerItem ans = answerMap.get(q.getId());

            String selected = ans == null ? null : normalizeChoice(ans.getSelectedAnswer());
            String text = ans == null ? null : safeTrim(ans.getTextAnswer(), 5000);

            Map<String, Object> stored = new LinkedHashMap<>();
            stored.put("questionId", q.getId());
            stored.put("selectedAnswer", selected);
            stored.put("textAnswer", text);

            AttemptReviewItemResponse ri = new AttemptReviewItemResponse();
            ri.setQuestionId(q.getId());
            ri.setQuestionType(q.getQuestionType());
            ri.setContent(q.getContent());

            if (q.getQuestionType() == QuestionType.MCQ) {
                int maxScore = Math.max(0, mcqMaxPointsByQid.getOrDefault(q.getId(), 0));
                totalPoints += maxScore;

                String correct = normalizeChoice(q.getCorrectAnswer());
                boolean isCorrect = correct != null && correct.equals(selected);

                int score = isCorrect ? maxScore : 0;
                earnedPoints += score;

                stored.put("earnedScore", score);
                stored.put("maxScore", maxScore);

                ri.setOptions(parseOptionsMap(q.getOptionsJson()));
                ri.setCorrectAnswer(q.getCorrectAnswer());
                ri.setSelectedAnswer(selected);
                ri.setIsCorrect(isCorrect);
                ri.setScore(score);
                ri.setMaxScore(maxScore);
                ri.setFeedback(isCorrect ? "Đúng" : "Sai");
            } else if (isEssayLikeType(q.getQuestionType())) {
                EssayAutoGradeResult essayResult = autoGradeEssay(q, text);

                int maxScore = Math.max(0, essayMaxPointsByQid.getOrDefault(q.getId(), 0));
                totalPoints += maxScore;

                int score = maxScore <= 0
                        ? 0
                        : (int) Math.round((essayResult.score / (double) Math.max(essayResult.maxScore, 1)) * maxScore);

                score = Math.max(0, Math.min(score, maxScore));
                earnedPoints += score;

                stored.put("earnedScore", score);
                stored.put("maxScore", maxScore);
                stored.put("rawEssayScore", essayResult.score);
                stored.put("rawEssayMaxScore", essayResult.maxScore);
                stored.put("autoFeedback", essayResult.feedback);

                ri.setOptions(null);
                ri.setCorrectAnswer(null);
                ri.setSelectedAnswer(null);
                ri.setYourAnswer(text);
                ri.setIsCorrect(maxScore > 0 && score >= maxScore);
                ri.setScore(score);
                ri.setMaxScore(maxScore);
                ri.setFeedback(essayResult.feedback);
            } else {
                stored.put("earnedScore", 0);
                stored.put("maxScore", 0);

                ri.setOptions(null);
                ri.setCorrectAnswer(null);
                ri.setSelectedAnswer(selected);
                ri.setYourAnswer(text);
                ri.setIsCorrect(null);
                ri.setScore(0);
                ri.setMaxScore(0);
                ri.setFeedback("Loại câu hỏi chưa hỗ trợ auto-grade.");
            }

            storedAnswers.add(stored);
            reviewItems.add(ri);
        }

        int scorePct = (int) Math.round((earnedPoints * 100.0) / Math.max(totalPoints, 1));
        scorePct = Math.max(0, Math.min(100, scorePct));

        ExamResult status = scorePct >= asg.getExam().getPassScore()
                ? ExamResult.PASSED
                : ExamResult.FAILED;

        attempt.setSubmitTime(now);
        attempt.setScore(scorePct);
        attempt.setStatus(status);
        attempt.setAnswersJson(writeJson(storedAnswers));

        String aiFeedback = null;
        String studyGuide = null;

        try {
            aiFeedback = aiPracticeFeedbackService.generateFeedback(me.getFullName(), scorePct, reviewItems);
        } catch (Exception ignored) {
        }

        try {
            String userResultJson = buildStudyGuideInputJson(questions, storedAnswers, scorePct);
            studyGuide = aiStudyGuideService.generateStudyGuide(me.getFullName(), userResultJson);
        } catch (Exception ignored) {
        }

        if (studyGuide == null || studyGuide.isBlank()) {
            try {
                studyGuide = aiStudyGuideService.fallbackStudyGuide(me.getFullName());
            } catch (Exception ignored) {
            }
        }

        attempt.setAiFeedback(aiFeedback);
        attempt.setStudyGuide(studyGuide);
        attemptRepo.save(attempt);

        asg.setStatus(AssignmentStatus.SUBMITTED);
        asg.setSubmittedAt(now);
        assignmentRepo.save(asg);

        SubmitPracticeResponse resp = new SubmitPracticeResponse();
        resp.setScore(scorePct);
        resp.setEarnedPoints(earnedPoints);
        resp.setTotalPoints(totalPoints);
        resp.setStatus(status);
        resp.setTimedOut(timedOut);
        resp.setFeedback(buildRuleBasedFeedback(scorePct));
        resp.setAiFeedback(aiFeedback);
        resp.setStudyGuide(studyGuide);
        return resp;
    }

    // =========================================================
    // STUDY GUIDE
    // =========================================================
    @Override
    @Transactional(readOnly = true)
    public String getStudyGuide(String email, Long assignmentId) {
        User me = requireMe(email);

        ExamAssignment asg = assignmentRepo.findByIdAndStudentId(assignmentId, me.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));

        if (asg.getAttempt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn chưa bắt đầu bài kiểm tra");
        }

        ExamAttempt attempt = attemptRepo.findByIdAndUserId(asg.getAttempt().getId(), me.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));

        return attempt.getStudyGuide();
    }

    // =========================================================
    // REVIEW
    // =========================================================
    @Override
    @Transactional(readOnly = true)
    public AttemptReviewResponse getReview(String email, Long assignmentId) {
        User me = requireMe(email);

        ExamAssignment asg = assignmentRepo.findByIdAndStudentId(assignmentId, me.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));

        if (asg.getAttempt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn chưa bắt đầu bài kiểm tra");
        }

        ExamAttempt attempt = attemptRepo.findByIdAndUserId(asg.getAttempt().getId(), me.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));

        if (attempt.getScore() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn chưa nộp bài");
        }

        List<Map<String, Object>> storedAnswers = readAnswersJson(attempt.getAnswersJson());
        Map<Long, Map<String, Object>> answerMap = indexByQuestionId(storedAnswers);

        List<ExamQuestion> eqs = examQuestionRepo.findAllByExamIdFetchQuestion(asg.getExam().getId());
        if (eqs == null || eqs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exam has no questions");
        }

        List<AttemptReviewItemResponse> items = new ArrayList<>();
        int correctCount = 0;
        int totalQuestions = 0;

        for (ExamQuestion eq : eqs) {
            Question q = eq.getQuestion();
            if (q == null) {
                continue;
            }

            totalQuestions++;

            Map<String, Object> ans = answerMap.get(q.getId());
            String selected = normalizeChoice(readString(ans, "selectedAnswer"));
            String text = safeTrim(readString(ans, "textAnswer"), 5000);
            Integer score = readInt(ans, "earnedScore");
            Integer maxScore = readInt(ans, "maxScore");

            AttemptReviewItemResponse ri = new AttemptReviewItemResponse();
            ri.setQuestionId(q.getId());
            ri.setQuestionType(q.getQuestionType());
            ri.setContent(q.getContent());
            ri.setScore(score == null ? 0 : score);
            ri.setMaxScore(maxScore == null ? 0 : maxScore);

            if (q.getQuestionType() == QuestionType.MCQ) {
                String correct = normalizeChoice(q.getCorrectAnswer());
                boolean isCorrect = correct != null && correct.equals(selected);
                if (isCorrect) {
                    correctCount++;
                }

                ri.setOptions(parseOptionsMap(q.getOptionsJson()));
                ri.setCorrectAnswer(q.getCorrectAnswer());
                ri.setSelectedAnswer(selected);
                ri.setIsCorrect(isCorrect);
                ri.setFeedback(readString(ans, "autoFeedback") != null ? readString(ans, "autoFeedback") : (isCorrect ? "Đúng " : "Sai "));
            } else {
                ri.setOptions(null);
                ri.setCorrectAnswer(null);
                ri.setSelectedAnswer(null);
                ri.setYourAnswer(text);

                String sampleAnswer = extractSampleAnswer(q.getAnalysis());
                ri.setSampleAnswer(sampleAnswer);

                boolean isPerfect = (maxScore != null && maxScore > 0) && (score != null && score >= maxScore);
                ri.setIsCorrect(isPerfect);

                String autoFeedback = safeTrim(readString(ans, "autoFeedback"), 2000);
                ri.setFeedback(autoFeedback != null ? autoFeedback : q.getAnalysis());
            }

            items.add(ri);
        }

        AttemptReviewResponse resp = new AttemptReviewResponse();
        resp.setScore(attempt.getScore());
        resp.setCorrectCount(correctCount);
        resp.setTotalQuestions(totalQuestions);
        resp.setItems(items);
        return resp;
    }

    // =========================================================
    // HELPERS
    // =========================================================
    private int effectiveDuration(ExamAssignment asg) {
        Integer o = asg.getDurationMinutesOverride();
        if (o != null && o > 0) {
            return o;
        }

        Integer d = asg.getExam().getDurationMinutes();
        if (d != null && d > 0) {
            return d;
        }

        return (int) Math.ceil(settingsService.getMinutesPerQuestion() * 10);
    }

    private String normalizeChoice(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim().toUpperCase(Locale.ROOT);
        if (t.startsWith("A")) return "A";
        if (t.startsWith("B")) return "B";
        if (t.startsWith("C")) return "C";
        if (t.startsWith("D")) return "D";
        if (t.startsWith("E")) return "E";
        if (t.startsWith("F")) return "F";
        if (t.startsWith("G")) return "G";
        if (t.startsWith("H")) return "H";
        return t.isBlank() ? null : t;
    }

    private String safeTrim(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.length() > max) {
            t = t.substring(0, max);
        }
        return t;
    }

    private String writeJson(Object obj) {
        try {
            return om.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String buildStudyGuideInputJson(
            List<Question> questions,
            List<? extends Map<String, ?>> storedAnswers,
            int scorePct
    ) {
        List<Map<String, Object>> qs = new ArrayList<>();
        for (Question q : questions) {
            if (q == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", q.getId());
            m.put("type", q.getQuestionType());
            m.put("question", safeTrim(q.getContent(), 600));
            m.put("analysis", safeTrim(q.getAnalysis(), 600));
            m.put("correctAnswer", q.getCorrectAnswer());
            qs.add(m);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scorePct", scorePct);
        payload.put("questions", qs);
        payload.put("answers", storedAnswers);

        return writeJson(payload);
    }

    private String buildRuleBasedFeedback(int scorePct) {
        if (scorePct >= 85) {
            return "Kết quả rất tốt. Bạn nắm khá chắc kiến thức và trình bày ổn.";
        }
        if (scorePct >= 70) {
            return "Kết quả khá tốt. Bạn đã hiểu phần lớn nội dung nhưng vẫn còn vài điểm cần củng cố.";
        }
        if (scorePct >= 50) {
            return "Bạn đã nắm được một phần kiến thức, nhưng cần ôn thêm các ý trọng tâm.";
        }
        return "Bạn cần xem lại kiến thức nền tảng và luyện thêm cả câu trắc nghiệm lẫn tự luận.";
    }

    private Map<String, String> parseOptionsMap(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return null;
        }

        String raw = optionsJson.trim();
        try {
            if (raw.startsWith("{")) {
                Map<String, String> m = om.readValue(raw, new TypeReference<Map<String, String>>() {});
                return (m == null || m.isEmpty()) ? null : normalizeOptionKeys(m);
            }

            if (raw.startsWith("[")) {
                List<String> list = om.readValue(raw, new TypeReference<List<String>>() {});
                if (list == null || list.isEmpty()) {
                    return null;
                }

                Map<String, String> m = new LinkedHashMap<>();
                String[] keys = {"A", "B", "C", "D", "E", "F", "G", "H"};
                for (int i = 0; i < list.size() && i < keys.length; i++) {
                    String v = list.get(i);
                    if (v == null) {
                        continue;
                    }
                    String vv = v.trim();
                    if (vv.isBlank()) {
                        continue;
                    }
                    m.put(keys[i], vv);
                }
                return m.isEmpty() ? null : m;
            }
        } catch (Exception ignored) {
        }

        Map<String, String> fb = new LinkedHashMap<>();
        fb.put("A", raw);
        return fb;
    }

    private Map<String, String> normalizeOptionKeys(Map<String, String> in) {
        if (in == null || in.isEmpty()) {
            return in;
        }

        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : in.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String k = e.getKey().trim().toUpperCase(Locale.ROOT);
            if (!k.isEmpty()) {
                k = String.valueOf(k.charAt(0));
            }
            String v = e.getValue() == null ? null : e.getValue().trim();
            if (v == null || v.isBlank()) {
                continue;
            }
            out.put(k, v);
        }
        return out.isEmpty() ? null : out;
    }

    private List<Map<String, Object>> readAnswersJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return om.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<Long, Map<String, Object>> indexByQuestionId(List<Map<String, Object>> list) {
        Map<Long, Map<String, Object>> m = new HashMap<>();
        for (Map<String, Object> a : list) {
            if (a == null) {
                continue;
            }
            Object idObj = a.get("questionId");
            if (idObj == null) {
                continue;
            }

            Long qid = null;
            try {
                qid = Long.valueOf(String.valueOf(idObj));
            } catch (Exception ignored) {
            }

            if (qid != null) {
                m.put(qid, a);
            }
        }
        return m;
    }

    private String readString(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object val = map.get(key);
        return val == null ? null : String.valueOf(val);
    }

    private Integer readInt(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object val = map.get(key);
        if (val == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isEssayLikeType(QuestionType type) {
        if (type == null) {
            return false;
        }
        return type == QuestionType.ESSAY || "SHORT_ANSWER".equalsIgnoreCase(type.name());
    }

    private Map<Long, Integer> allocatePointsByQuestionId(List<Question> questions, int totalPoints) {
        Map<Long, Integer> map = new HashMap<>();
        if (questions == null || questions.isEmpty() || totalPoints <= 0) {
            return map;
        }

        int count = questions.size();
        int base = totalPoints / count;
        int rem = totalPoints % count;

        for (int i = 0; i < count; i++) {
            int pts = base + (i < rem ? 1 : 0);
            map.put(questions.get(i).getId(), pts);
        }
        return map;
    }

    private String extractSampleAnswer(String analysisJson) {
        EssayRubric rubric = parseEssayRubric(analysisJson);
        return rubric.sampleAnswer;
    }

    // =========================================================
    // ESSAY AUTO GRADING
    // =========================================================
    private static class EssayRubric {
        private final String sampleAnswer;
        private final List<String> keywords;
        private final int maxScore;

        private EssayRubric(String sampleAnswer, List<String> keywords, int maxScore) {
            this.sampleAnswer = sampleAnswer;
            this.keywords = keywords == null ? List.of() : keywords;
            this.maxScore = Math.max(1, maxScore);
        }
    }

    private static class EssayAutoGradeResult {
        private final int score;
        private final int maxScore;
        private final String feedback;

        private EssayAutoGradeResult(int score, int maxScore, String feedback) {
            this.score = score;
            this.maxScore = maxScore;
            this.feedback = feedback;
        }
    }

    private EssayRubric parseEssayRubric(String analysisJson) {
        if (analysisJson == null || analysisJson.isBlank()) {
            return new EssayRubric(null, List.of(), 10);
        }

        try {
            Map<String, Object> m = om.readValue(analysisJson, new TypeReference<Map<String, Object>>() {});

            String sampleAnswer = m.get("sampleAnswer") == null
                    ? null
                    : String.valueOf(m.get("sampleAnswer")).trim();

            List<String> keywords = new ArrayList<>();
            Object keywordsObj = m.get("keywords");
            if (keywordsObj instanceof List<?> list) {
                for (Object x : list) {
                    if (x == null) {
                        continue;
                    }
                    String kw = String.valueOf(x).trim();
                    if (!kw.isBlank()) {
                        keywords.add(kw);
                    }
                }
            }

            int maxScore = 10;
            Object maxScoreObj = m.get("maxScore");
            if (maxScoreObj != null) {
                try {
                    maxScore = Integer.parseInt(String.valueOf(maxScoreObj).trim());
                } catch (Exception ignored) {
                }
            }

            return new EssayRubric(sampleAnswer, keywords, maxScore);
        } catch (Exception e) {
            return new EssayRubric(null, List.of(), 10);
        }
    }

    private EssayAutoGradeResult autoGradeEssay(Question q, String answerText) {
        EssayRubric rubric = parseEssayRubric(q.getAnalysis());

        String answer = safeTrim(answerText, 5000);
        if (answer == null || answer.isBlank()) {
            return new EssayAutoGradeResult(
                    0,
                    rubric.maxScore,
                    "Chưa có câu trả lời tự luận."
            );
        }

        String normalizedAnswer = normalizeText(answer);
        String normalizedSample = normalizeText(rubric.sampleAnswer);

        double keywordRatio = calcKeywordCoverage(normalizedAnswer, rubric.keywords);
        double sampleSimilarity = calcSimpleSimilarity(normalizedAnswer, normalizedSample);
        boolean hasGoodLength = normalizedAnswer.length() >= 20;

        // ✅ Chặn trả lời linh tinh / không đúng ý
        // quá ngắn hoặc không có keyword nào hoặc quá xa đáp án mẫu => 0 điểm
        if (!hasGoodLength) {
            return new EssayAutoGradeResult(
                    0,
                    rubric.maxScore,
                    "Câu trả lời quá ngắn hoặc chưa đủ ý để chấm điểm."
            );
        }

        if (keywordRatio <= 0.0 && sampleSimilarity < 0.08) {
            return new EssayAutoGradeResult(
                    0,
                    rubric.maxScore,
                    "Câu trả lời không đúng trọng tâm hoặc không khớp ý chính của đáp án mẫu."
            );
        }

        int keywordScore = (int) Math.round(rubric.maxScore * 0.7 * keywordRatio);
        int lengthScore = hasGoodLength ? (int) Math.round(rubric.maxScore * 0.2) : 0;
        int similarityScore = (int) Math.round(rubric.maxScore * 0.1 * sampleSimilarity);

        int totalScore = keywordScore + lengthScore + similarityScore;
        totalScore = Math.max(0, Math.min(rubric.maxScore, totalScore));

        String feedback = buildEssayFeedback(
                rubric,
                keywordRatio,
                sampleSimilarity,
                hasGoodLength,
                totalScore,
                rubric.maxScore
        );

        return new EssayAutoGradeResult(totalScore, rubric.maxScore, feedback);
    }

    private String normalizeText(String s) {
        if (s == null) {
            return "";
        }

        String noAccent = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        return noAccent.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private double calcKeywordCoverage(String normalizedAnswer, List<String> keywords) {
        if (normalizedAnswer == null || normalizedAnswer.isBlank()) {
            return 0.0;
        }

        if (keywords == null || keywords.isEmpty()) {
            return 0.0;
        }

        int hit = 0;
        int total = 0;

        for (String kw : keywords) {
            if (kw == null || kw.isBlank()) {
                continue;
            }

            String normalizedKw = normalizeText(kw);
            if (normalizedKw.isBlank()) {
                continue;
            }

            total++;
            if (normalizedAnswer.contains(normalizedKw)) {
                hit++;
            }
        }

        if (total == 0) {
            return 0.6;
        }

        return Math.max(0.0, Math.min(1.0, (double) hit / total));
    }

    private double calcSimpleSimilarity(String a, String b) {
        if (a == null || a.isBlank() || b == null || b.isBlank()) {
            return 0.0;
        }

        Set<String> wa = new HashSet<>(Arrays.asList(a.split("\\s+")));
        Set<String> wb = new HashSet<>(Arrays.asList(b.split("\\s+")));

        wa.removeIf(String::isBlank);
        wb.removeIf(String::isBlank);

        if (wa.isEmpty() || wb.isEmpty()) {
            return 0.0;
        }

        Set<String> inter = new HashSet<>(wa);
        inter.retainAll(wb);

        Set<String> union = new HashSet<>(wa);
        union.addAll(wb);

        if (union.isEmpty()) {
            return 0.0;
        }

        return (double) inter.size() / union.size();
    }

    private String buildEssayFeedback(
            EssayRubric rubric,
            double keywordRatio,
            double sampleSimilarity,
            boolean hasGoodLength,
            int score,
            int maxScore
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Điểm tự động: ").append(score).append("/").append(maxScore).append(". ");

        if (keywordRatio >= 0.8) {
            sb.append("Câu trả lời bao phủ tốt các ý chính. ");
        } else if (keywordRatio >= 0.5) {
            sb.append("Câu trả lời đã có một phần ý chính nhưng còn thiếu một số ý quan trọng. ");
        } else {
            sb.append("Câu trả lời còn thiếu nhiều từ khóa hoặc ý chính quan trọng. ");
        }

        if (hasGoodLength) {
            sb.append("Độ dài câu trả lời tương đối ổn. ");
        } else {
            sb.append("Câu trả lời còn ngắn, nên triển khai ý rõ hơn. ");
        }

        if (sampleSimilarity >= 0.5) {
            sb.append("Nội dung khá sát với đáp án mẫu.");
        } else if (sampleSimilarity >= 0.25) {
            sb.append("Nội dung có liên quan đến đáp án mẫu nhưng chưa đủ đầy.");
        } else {
            sb.append("Nội dung còn khá xa đáp án mẫu.");
        }

        if (rubric.keywords != null && !rubric.keywords.isEmpty()) {
            List<String> previewKeywords = rubric.keywords.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .limit(5)
                    .collect(Collectors.toList());

            if (!previewKeywords.isEmpty()) {
                sb.append(" Từ khóa trọng tâm: ").append(String.join(", ", previewKeywords)).append(".");
            }
        }

        return sb.toString().trim();
    }
}