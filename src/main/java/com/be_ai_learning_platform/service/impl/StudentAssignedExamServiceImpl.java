package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.request.SubmitPracticeRequest;
import com.be_ai_learning_platform.dto.response.*;
import com.be_ai_learning_platform.entity.*;
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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StudentAssignedExamServiceImpl implements StudentAssignedExamService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

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

                    // keep VN-local LocalDateTime (already stored as VN by Admin service)
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

        // VN compare (avoid server timezone mismatch)
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
                    if (q == null) return null;

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

        // Deadline calculated in VN-local LocalDateTime
        resp.setDeadline(attempt.getStartTime().plusMinutes(resp.getDurationMinutes()));
        resp.setQuestions(questions);
        return resp;
    }

    // =========================================================
    // SUBMIT
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
                if (a == null || a.getQuestionId() == null) continue;
                answerMap.put(a.getQuestionId(), a);
            }
        }

        int earnedPoints = 0;
        List<Map<String, Object>> storedAnswers = new ArrayList<>();
        List<AttemptReviewItemResponse> reviewItems = new ArrayList<>();

        long mcqCount = eqs.stream()
                .map(ExamQuestion::getQuestion)
                .filter(Objects::nonNull)
                .filter(q -> q.getQuestionType() == QuestionType.MCQ)
                .count();

        int mcqPointEach = mcqCount <= 0 ? 0 : (int) Math.floor(100.0 / mcqCount);

        for (ExamQuestion eq : eqs) {
            Question q = eq.getQuestion();
            if (q == null) continue;

            SubmitPracticeRequest.AnswerItem ans = answerMap.get(q.getId());

            String selected = ans == null ? null : normalizeChoice(ans.getSelectedAnswer());
            String text = ans == null ? null : safeTrim(ans.getTextAnswer(), 5000);

            boolean isCorrect = false;
            if (q.getQuestionType() == QuestionType.MCQ) {
                String correct = normalizeChoice(q.getCorrectAnswer());
                isCorrect = correct != null && correct.equals(selected);
                if (isCorrect) earnedPoints += mcqPointEach;
            }

            Map<String, Object> a = new LinkedHashMap<>();
            a.put("questionId", q.getId());
            a.put("selectedAnswer", selected);
            a.put("textAnswer", text);
            storedAnswers.add(a);

            AttemptReviewItemResponse ri = new AttemptReviewItemResponse();
            ri.setQuestionId(q.getId());
            ri.setQuestionType(q.getQuestionType());
            ri.setContent(q.getContent());

            if (q.getQuestionType() == QuestionType.MCQ) {
                ri.setOptions(parseOptionsMap(q.getOptionsJson()));
                ri.setCorrectAnswer(q.getCorrectAnswer());
                ri.setSelectedAnswer(selected);
                ri.setIsCorrect(isCorrect);
                ri.setFeedback(q.getAnalysis());
            } else {
                ri.setOptions(null);
                ri.setCorrectAnswer(null);
                ri.setSelectedAnswer(null);
                ri.setYourAnswer(text);
                ri.setIsCorrect(null);
                ri.setFeedback(q.getAnalysis());
            }

            reviewItems.add(ri);
        }

        int scorePct = Math.max(0, Math.min(100, earnedPoints));
        ExamResult status = scorePct >= asg.getExam().getPassScore() ? ExamResult.PASSED : ExamResult.FAILED;

        attempt.setSubmitTime(now);
        attempt.setScore(scorePct);
        attempt.setStatus(status);
        attempt.setAnswersJson(writeJson(storedAnswers));

        String aiFeedback = null;
        String studyGuide = null;

        try {
            aiFeedback = aiPracticeFeedbackService.generateFeedback(me.getFullName(), scorePct, reviewItems);
        } catch (Exception ignored) {}

        try {
            List<Question> questions = eqs.stream()
                    .map(ExamQuestion::getQuestion)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            String userResultJson = buildStudyGuideInputJson(questions, storedAnswers, scorePct);
            studyGuide = aiStudyGuideService.generateStudyGuide(me.getFullName(), userResultJson);
        } catch (Exception ignored) {}

        if (studyGuide == null || studyGuide.isBlank()) {
            try {
                studyGuide = aiStudyGuideService.fallbackStudyGuide(me.getFullName());
            } catch (Exception ignored) {}
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
        resp.setTotalPoints(100);
        resp.setStatus(status);
        resp.setTimedOut(timedOut);
        resp.setFeedback(buildRuleBasedFeedback(reviewItems));
        resp.setAiFeedback(aiFeedback);
        resp.setStudyGuide(studyGuide);
        return resp;
    }

    // =========================================================
    // helpers
    // =========================================================
    private int effectiveDuration(ExamAssignment asg) {
        Integer o = asg.getDurationMinutesOverride();
        if (o != null && o > 0) return o;

        Integer d = asg.getExam().getDurationMinutes();
        if (d != null && d > 0) return d;

        return (int) Math.ceil(settingsService.getMinutesPerQuestion() * 10);
    }

    private String normalizeChoice(String raw) {
        if (raw == null) return null;
        String t = raw.trim().toUpperCase(Locale.ROOT);
        if (t.startsWith("A")) return "A";
        if (t.startsWith("B")) return "B";
        if (t.startsWith("C")) return "C";
        if (t.startsWith("D")) return "D";
        return t.isBlank() ? null : t;
    }

    private String safeTrim(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() > max) t = t.substring(0, max);
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
            if (q == null) continue;
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

    private String buildRuleBasedFeedback(List<AttemptReviewItemResponse> items) {
        long total = items == null ? 0 : items.size();
        long correct = items == null ? 0 : items.stream()
                .filter(i -> Boolean.TRUE.equals(i.getIsCorrect()))
                .count();

        return "Bạn làm đúng " + correct + "/" + total + " câu. Hãy xem lại các câu sai và ôn lại phần kiến thức liên quan.";
    }

    private Map<String, String> parseOptionsMap(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) return null;

        String raw = optionsJson.trim();
        try {
            if (raw.startsWith("{")) {
                Map<String, String> m = om.readValue(raw, new TypeReference<Map<String, String>>() {});
                return (m == null || m.isEmpty()) ? null : normalizeOptionKeys(m);
            }

            if (raw.startsWith("[")) {
                List<String> list = om.readValue(raw, new TypeReference<List<String>>() {});
                if (list == null || list.isEmpty()) return null;

                Map<String, String> m = new LinkedHashMap<>();
                String[] keys = {"A", "B", "C", "D", "E", "F", "G", "H"};
                for (int i = 0; i < list.size() && i < keys.length; i++) {
                    String v = list.get(i);
                    if (v == null) continue;
                    String vv = v.trim();
                    if (vv.isBlank()) continue;
                    m.put(keys[i], vv);
                }
                return m.isEmpty() ? null : m;
            }
        } catch (Exception ignored) {}

        Map<String, String> fb = new LinkedHashMap<>();
        fb.put("A", raw);
        return fb;
    }

    private Map<String, String> normalizeOptionKeys(Map<String, String> in) {
        if (in == null || in.isEmpty()) return in;

        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : in.entrySet()) {
            if (e.getKey() == null) continue;
            String k = e.getKey().trim().toUpperCase(Locale.ROOT);
            if (!k.isEmpty()) k = String.valueOf(k.charAt(0));
            String v = e.getValue() == null ? null : e.getValue().trim();
            if (v == null || v.isBlank()) continue;
            out.put(k, v);
        }
        return out.isEmpty() ? null : out;
    }
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

        // đã generate lúc submit rồi
        return attempt.getStudyGuide();
    }

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
        int totalMcq = 0;

        for (ExamQuestion eq : eqs) {
            Question q = eq.getQuestion();
            if (q == null) continue;

            Map<String, Object> ans = answerMap.get(q.getId());
            String selected = ans == null ? null : normalizeChoice((String) ans.get("selectedAnswer"));
            String text = ans == null ? null : safeTrim((String) ans.get("textAnswer"), 5000);

            AttemptReviewItemResponse ri = new AttemptReviewItemResponse();
            ri.setQuestionId(q.getId());
            ri.setQuestionType(q.getQuestionType());
            ri.setContent(q.getContent());

            if (q.getQuestionType() == QuestionType.MCQ) {
                totalMcq++;
                String correct = normalizeChoice(q.getCorrectAnswer());
                boolean isCorrect = correct != null && correct.equals(selected);
                if (isCorrect) correctCount++;

                ri.setOptions(parseOptionsMap(q.getOptionsJson()));
                ri.setCorrectAnswer(q.getCorrectAnswer());
                ri.setSelectedAnswer(selected);
                ri.setIsCorrect(isCorrect);
                ri.setFeedback(q.getAnalysis());
            } else {
                ri.setOptions(null);
                ri.setCorrectAnswer(null);
                ri.setSelectedAnswer(null);
                ri.setYourAnswer(text);
                ri.setIsCorrect(null);
                ri.setFeedback(q.getAnalysis());
            }

            items.add(ri);
        }

        AttemptReviewResponse resp = new AttemptReviewResponse();
        resp.setScore(attempt.getScore());
        resp.setCorrectCount(correctCount);
        resp.setTotalQuestions(totalMcq); // PracticeReviewDialog dùng cái này
        resp.setItems(items);
        return resp;
    }

    private List<Map<String, Object>> readAnswersJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return om.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<Long, Map<String, Object>> indexByQuestionId(List<Map<String, Object>> list) {
        Map<Long, Map<String, Object>> m = new HashMap<>();
        for (Map<String, Object> a : list) {
            if (a == null) continue;
            Object idObj = a.get("questionId");
            if (idObj == null) continue;
            Long qid = null;
            try { qid = Long.valueOf(String.valueOf(idObj)); } catch (Exception ignored) {}
            if (qid != null) m.put(qid, a);
        }
        return m;
    }
}