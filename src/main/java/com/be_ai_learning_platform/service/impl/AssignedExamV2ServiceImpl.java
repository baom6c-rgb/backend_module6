package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.request.GenerateAssignedExamSessionRequest;
import com.be_ai_learning_platform.dto.request.StartPracticeSessionRequest;
import com.be_ai_learning_platform.dto.request.SubmitPracticeSessionRequest;
import com.be_ai_learning_platform.dto.response.GeneratePracticeSessionResponse;
import com.be_ai_learning_platform.dto.response.PracticeQuestionV2Response;
import com.be_ai_learning_platform.dto.response.StartPracticeSessionResponse;
import com.be_ai_learning_platform.dto.response.SubmitPracticeV2Response;
import com.be_ai_learning_platform.entity.Exam;
import com.be_ai_learning_platform.entity.ExamAssignment;
import com.be_ai_learning_platform.entity.ExamAttempt;
import com.be_ai_learning_platform.entity.ExamQuestion;
import com.be_ai_learning_platform.entity.Question;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.AssignmentStatus;
import com.be_ai_learning_platform.entity.enums.ExamResult;
import com.be_ai_learning_platform.entity.enums.QuestionType;
import com.be_ai_learning_platform.repository.ExamAssignmentRepository;
import com.be_ai_learning_platform.repository.ExamAttemptRepository;
import com.be_ai_learning_platform.repository.ExamQuestionRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.AssignedExamV2Service;
import com.be_ai_learning_platform.service.SystemSettingsService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssignedExamV2ServiceImpl implements AssignedExamV2Service {

    private static final String PREFIX = "ASSIGNED_V2:";

    // ✅ VN timezone (fix lệch giờ server/DB)
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final Cache<String, Object> practiceSessionCache; // reuse same cache bean
    private final ObjectMapper om;

    private final UserRepository userRepo;
    private final ExamAssignmentRepository assignmentRepo;
    private final ExamQuestionRepository examQuestionRepo;
    private final ExamAttemptRepository attemptRepo;
    private final SystemSettingsService settingsService;

    // ========================= TIME HELPERS =========================
    private LocalDateTime nowVn() {
        return LocalDateTime.now(VN_ZONE).withNano(0);
    }

    private LocalDateTime normalize(LocalDateTime dt) {
        return dt == null ? null : dt.withNano(0);
    }

    // ========================= SESSION CACHE =========================
    private static class SessionData {
        String token;
        Long studentId;
        Long assignmentId;
        Long examId;
        String examTitle;
        Integer durationMinutes;
        LocalDateTime startedAt;
        LocalDateTime deadline;
        boolean submitted;

        List<Q> questions = new ArrayList<>();
    }

    private static class Q {
        String key;
        Long questionId;
        QuestionType type;
        String content;
        Map<String, String> options; // MCQ only
        String correct;              // MCQ only (normalized)
        String rubricJson;           // ESSAY only
        Integer maxScore;            // optional
    }

    private String cacheKey(String token) {
        return PREFIX + token;
    }

    private User me(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    // =========================================================
    // GENERATE
    // =========================================================
    @Override
    public GeneratePracticeSessionResponse generateAssignedSessionV2(String email, GenerateAssignedExamSessionRequest req) {
        if (req == null || req.getAssignmentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assignmentId is required");
        }

        User me = me(email);

        ExamAssignment ea = assignmentRepo.findByIdAndStudentId(req.getAssignmentId(), me.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));

        if (ea.getStatus() == AssignmentStatus.CANCELED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment is canceled");
        }

        // ✅ Nếu đã nộp rồi thì chặn luôn
        if (ea.getStatus() == AssignmentStatus.SUBMITTED || ea.getSubmittedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment already submitted");
        }

        // ✅ FIX: VN now
        LocalDateTime now = nowVn();

        // Normalize stored times for consistent compare
        LocalDateTime openAt = normalize(ea.getOpenAt());
        LocalDateTime dueAt = normalize(ea.getDueAt());

        if (openAt != null && now.isBefore(openAt)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Assignment not opened yet");
        }
        if (dueAt != null && now.isAfter(dueAt)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Assignment is overdue");
        }

        Exam exam = ea.getExam();
        if (exam == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exam not found");
        }

        List<ExamQuestion> eqs = examQuestionRepo.findAllByExamIdFetchQuestion(exam.getId());
        if (eqs == null || eqs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Exam has no questions");
        }

        SessionData s = new SessionData();
        s.token = UUID.randomUUID().toString().replace("-", "");
        s.studentId = me.getId();
        s.assignmentId = ea.getId();
        s.examId = exam.getId();
        s.examTitle = exam.getTitle();

        Integer duration = (ea.getDurationMinutesOverride() != null && ea.getDurationMinutesOverride() > 0)
                ? ea.getDurationMinutesOverride()
                : exam.getDurationMinutes();

        s.durationMinutes = duration;

        int idx = 1;
        for (ExamQuestion eq : eqs) {
            Question q = eq.getQuestion();
            if (q == null) continue;

            Q item = new Q();
            item.key = "Q" + idx++;
            item.questionId = q.getId();
            item.type = q.getQuestionType();
            item.content = q.getContent();

            if (q.getQuestionType() == QuestionType.MCQ) {
                item.correct = normalizeChoice(q.getCorrectAnswer());
                item.options = readOptions(q.getOptionsJson());
            } else {
                // bạn đang dùng analysis như rubricJson
                item.rubricJson = q.getAnalysis();
            }

            s.questions.add(item);
        }

        practiceSessionCache.put(cacheKey(s.token), s);

        GeneratePracticeSessionResponse res = new GeneratePracticeSessionResponse();
        res.setStatus("READY");
        res.setSessionToken(s.token);
        res.setMaterialId(null);
        res.setNumberOfQuestions(s.questions.size());
        res.setDurationMinutes(s.durationMinutes);
        return res;
    }

    // =========================================================
    // START
    // =========================================================
    @Override
    public StartPracticeSessionResponse startAssignedSessionV2(String email, StartPracticeSessionRequest req) {
        if (req == null || req.getSessionToken() == null || req.getSessionToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionToken is required");
        }

        User me = me(email);
        SessionData s = getSession(req.getSessionToken());

        if (!Objects.equals(s.studentId, me.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session does not belong to current user");
        }
        if (s.submitted) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session already submitted");
        }

        ExamAssignment ea = assignmentRepo.findByIdAndStudentId(s.assignmentId, me.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));

        if (ea.getStatus() == AssignmentStatus.CANCELED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment is canceled");
        }
        if (ea.getStatus() == AssignmentStatus.SUBMITTED || ea.getSubmittedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment already submitted");
        }

        // ✅ FIX: VN now
        LocalDateTime now = nowVn();

        LocalDateTime openAt = normalize(ea.getOpenAt());
        LocalDateTime dueAt = normalize(ea.getDueAt());

        if (openAt != null && now.isBefore(openAt)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Assignment not opened yet");
        }
        if (dueAt != null && now.isAfter(dueAt)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Assignment is overdue");
        }

        if (s.startedAt == null) {
            s.startedAt = now;

            LocalDateTime deadline = (s.durationMinutes == null) ? null : now.plusMinutes(s.durationMinutes);

            // hard dueAt: deadline không được vượt quá dueAt
            if (dueAt != null && deadline != null && deadline.isAfter(dueAt)) {
                deadline = dueAt;
            }
            s.deadline = normalize(deadline);

            // Optional: lưu startedAt vào assignment để admin thấy ai đã start
            if (ea.getStartedAt() == null) {
                ea.setStartedAt(now);
                ea.setStatus(AssignmentStatus.STARTED); // ✅ hợp lý hơn cho tracking admin
                assignmentRepo.save(ea);
            }

            practiceSessionCache.put(cacheKey(s.token), s);
        }

        StartPracticeSessionResponse res = new StartPracticeSessionResponse();
        res.setSessionToken(s.token);
        res.setDurationMinutes(s.durationMinutes);
        res.setStartedAt(s.startedAt);
        res.setDeadline(s.deadline);
        res.setExamTitle(s.examTitle);
        res.setQuestions(toQuestionResponses(s.questions));
        return res;
    }

    // =========================================================
    // GET SESSION
    // =========================================================
    @Override
    public StartPracticeSessionResponse getAssignedSessionV2(String email, String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionToken is required");
        }

        User me = me(email);
        SessionData s = getSession(sessionToken);

        if (!Objects.equals(s.studentId, me.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session does not belong to current user");
        }

        StartPracticeSessionResponse res = new StartPracticeSessionResponse();
        res.setSessionToken(s.token);
        res.setDurationMinutes(s.durationMinutes);
        res.setStartedAt(s.startedAt);
        res.setDeadline(s.deadline);
        res.setExamTitle(s.examTitle);
        res.setQuestions(toQuestionResponses(s.questions));
        return res;
    }

    // =========================================================
    // SUBMIT
    // =========================================================
    @Transactional
    @Override
    public SubmitPracticeV2Response submitAssignedSessionV2(String email, String sessionToken, SubmitPracticeSessionRequest req) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionToken is required");
        }
        if (req == null || req.getAnswers() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Answers is required");
        }

        User me = me(email);
        SessionData s = getSession(sessionToken);

        if (!Objects.equals(s.studentId, me.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session does not belong to current user");
        }
        if (s.startedAt == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is not started yet");
        }
        if (s.submitted) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session already submitted");
        }

        ExamAssignment ea = assignmentRepo.findByIdAndStudentId(s.assignmentId, me.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));

        if (ea.getStatus() == AssignmentStatus.CANCELED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment is canceled");
        }
        if (ea.getStatus() == AssignmentStatus.SUBMITTED || ea.getSubmittedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment already submitted");
        }

        // ✅ FIX: VN now
        LocalDateTime now = nowVn();

        LocalDateTime dueAt = normalize(ea.getDueAt());
        if (dueAt != null && now.isAfter(dueAt)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Assignment is overdue");
        }

        boolean timedOut = s.deadline != null && now.isAfter(s.deadline);

        Map<String, SubmitPracticeSessionRequest.AnswerItem> ansByKey = req.getAnswers().stream()
                .filter(a -> a.getQuestionKey() != null && !a.getQuestionKey().isBlank())
                .collect(Collectors.toMap(
                        SubmitPracticeSessionRequest.AnswerItem::getQuestionKey,
                        a -> a,
                        (a, b) -> b
                ));

        int totalPoints = 100;
        int earned = 0;

        List<Map<String, Object>> answersForJson = new ArrayList<>();

        int perQuestionMax = totalPoints / Math.max(1, s.questions.size());

        for (Q q : s.questions) {
            SubmitPracticeSessionRequest.AnswerItem a = ansByKey.get(q.key);

            int max = perQuestionMax;

            if (q.type == QuestionType.MCQ) {
                String selected = a == null ? null : normalizeChoice(a.getSelectedAnswer());
                boolean correct = selected != null && selected.equals(q.correct);
                int score = correct ? max : 0;
                earned += score;

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("questionId", q.questionId);
                row.put("questionType", "MCQ");
                row.put("selectedAnswer", selected);
                row.put("correctAnswer", q.correct);
                row.put("score", score);
                row.put("maxScore", max);
                row.put("feedback", correct ? "Đúng" : "Sai");
                answersForJson.add(row);
            } else {
                String text = a == null ? null : safe(a.getTextAnswer());
                int score = scoreEssay(text, q.rubricJson, max);
                earned += score;

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("questionId", q.questionId);
                row.put("questionType", "ESSAY");
                row.put("textAnswer", text);
                row.put("score", score);
                row.put("maxScore", max);
                row.put("feedback", "Đã ghi nhận");
                answersForJson.add(row);
            }
        }

        int scorePct = Math.min(100, Math.max(0, earned));
        int pass = settingsService.getPassScore();

        Exam exam = ea.getExam();
        if (exam == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exam not found");
        }

        ExamAttempt attempt = new ExamAttempt();
        attempt.setUser(me);
        attempt.setExam(exam);
        attempt.setClassroom(me.getClassName());
        attempt.setLearningModule(me.getLearningModule());

        // ✅ startedAt đã theo VN
        attempt.setStartTime(normalize(s.startedAt));
        attempt.setSubmitTime(now);

        attempt.setAnswersJson(writeJson(answersForJson));
        attempt.setScore(scorePct);
        attempt.setStatus(scorePct >= pass ? ExamResult.PASSED : ExamResult.FAILED);

        attempt = attemptRepo.save(attempt);

        // mark assignment
        ea.setAttempt(attempt);
        ea.setStatus(AssignmentStatus.SUBMITTED);
        ea.setSubmittedAt(now);
        assignmentRepo.save(ea);

        s.submitted = true;
        practiceSessionCache.put(cacheKey(s.token), s);

        SubmitPracticeV2Response res = new SubmitPracticeV2Response();
        res.setAttemptId(attempt.getId());
        res.setExamId(exam.getId());
        res.setExamTitle(exam.getTitle());
        res.setScore(scorePct);
        res.setEarnedPoints(earned);
        res.setTotalPoints(totalPoints);
        res.setStatus(attempt.getStatus());
        res.setTimedOut(timedOut);
        res.setFeedback(scorePct >= pass ? "Đạt" : "Chưa đạt");
        res.setAiFeedback(null);
        res.setStudyGuide(null);
        return res;
    }

    // =========================================================
    // INTERNAL
    // =========================================================
    private SessionData getSession(String token) {
        Object raw = practiceSessionCache.getIfPresent(cacheKey(token));
        if (!(raw instanceof SessionData s)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found/expired");
        }
        return s;
    }

    private List<PracticeQuestionV2Response> toQuestionResponses(List<Q> qs) {
        List<PracticeQuestionV2Response> out = new ArrayList<>();
        for (Q q : qs) {
            PracticeQuestionV2Response r = new PracticeQuestionV2Response();
            r.setQuestionKey(q.key);
            r.setQuestionType(q.type);
            r.setContent(q.content);
            r.setOptions(q.options);
            out.add(r);
        }
        return out;
    }

    private Map<String, String> readOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) return null;

        String raw = optionsJson.trim();

        try {
            if (raw.startsWith("{")) {
                Map<String, String> map = om.readValue(raw, new TypeReference<Map<String, String>>() {});
                return (map == null || map.isEmpty()) ? null : normalizeOptionKeys(map);
            }
        } catch (Exception ignored) {}

        try {
            if (raw.startsWith("[")) {
                List<String> list = om.readValue(raw, new TypeReference<List<String>>() {});
                if (list == null || list.isEmpty()) return null;

                Map<String, String> out = new LinkedHashMap<>();
                char c = 'A';
                for (String s : list) {
                    if (s == null) continue;
                    String v = s.trim();
                    if (v.isBlank()) continue;
                    out.put(String.valueOf(c++), v);
                    if (c > 'H') break;
                }
                return out.isEmpty() ? null : out;
            }
        } catch (Exception ignored) {}

        return null;
    }

    private Map<String, String> normalizeOptionKeys(Map<String, String> in) {
        if (in == null || in.isEmpty()) return in;

        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : in.entrySet()) {
            if (e.getKey() == null) continue;
            String k = e.getKey().trim().toUpperCase(Locale.ROOT);
            if (!k.isEmpty()) k = String.valueOf(k.charAt(0)); // A/B/C...
            String v = e.getValue() == null ? null : e.getValue().trim();
            if (v == null || v.isBlank()) continue;
            out.put(k, v);
        }
        return out.isEmpty() ? null : out;
    }

    private String writeJson(Object obj) {
        try {
            return om.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private String normalizeChoice(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        char c = Character.toUpperCase(t.charAt(0));
        if (c >= 'A' && c <= 'H') return String.valueOf(c);
        return t.toUpperCase(Locale.ROOT);
    }

    private int scoreEssay(String text, String rubricJson, int max) {
        if (text == null || text.isBlank()) return 0;
        if (rubricJson == null || rubricJson.isBlank()) return Math.max(0, Math.min(max, max / 2));

        try {
            Map<String, Object> map = om.readValue(rubricJson, new TypeReference<Map<String, Object>>() {});
            Object kw = map.get("keywords");
            if (kw instanceof List<?> list && !list.isEmpty()) {
                String lower = text.toLowerCase(Locale.ROOT);
                int hit = 0;
                for (Object o : list) {
                    if (o == null) continue;
                    String k = String.valueOf(o).toLowerCase(Locale.ROOT).trim();
                    if (!k.isEmpty() && lower.contains(k)) hit++;
                }
                double ratio = (double) hit / (double) list.size();
                return (int) Math.round(max * Math.min(1.0, Math.max(0.0, ratio)));
            }
        } catch (Exception ignored) {}

        return Math.max(0, Math.min(max, max / 2));
    }
}