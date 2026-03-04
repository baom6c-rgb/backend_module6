package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.request.AdminCreateAssignedExamRequest;
import com.be_ai_learning_platform.dto.request.AdminExamPreviewRequest;
import com.be_ai_learning_platform.dto.request.AdminUpdateAssignedExamRequest;
import com.be_ai_learning_platform.dto.response.*;
import com.be_ai_learning_platform.entity.*;
import com.be_ai_learning_platform.entity.enums.AssignmentStatus;
import com.be_ai_learning_platform.entity.enums.ExamType;
import com.be_ai_learning_platform.entity.enums.MaterialStatus;
import com.be_ai_learning_platform.entity.enums.QuestionType;
import com.be_ai_learning_platform.repository.*;
import com.be_ai_learning_platform.service.AdminAssignedExamService;
import com.be_ai_learning_platform.service.QuestionGenerationService;
import com.be_ai_learning_platform.service.SystemSettingsService;
import com.be_ai_learning_platform.service.mail.MailService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminAssignedExamServiceImpl implements AdminAssignedExamService {

    // ========================= TIMEZONE =========================
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private LocalDateTime nowVn() {
        return LocalDateTime.now(VN_ZONE).withNano(0);
    }

    private LocalDateTime normalizeVn(LocalDateTime dt) {
        return dt == null ? null : dt.withNano(0);
    }

    // ========================= LIMITS =========================
    private static final int MAX_TITLE_CHARS = 120;
    private static final int MAX_TEXT_CHARS = 20000;
    private static final int MAX_QUESTIONS = 30;
    private static final int MIN_DURATION_MINUTES = 5;
    private static final int MAX_DURATION_MINUTES = 180;

    private final UserRepository userRepo;
    private final LearningMaterialRepository materialRepo;

    private final ExamRepository examRepo;
    private final QuestionRepository questionRepo;
    private final ExamQuestionRepository examQuestionRepo;
    private final ExamAssignmentRepository assignmentRepo;
    private final ExamAttemptRepository attemptRepo;
    private final CheatingEventRepository cheatingEventRepo;

    private final QuestionGenerationService questionGenerationService;
    private final SystemSettingsService settingsService;
    private final MailService mailService;
    private final ObjectMapper om;
    private final Cache<String, Object> practicePreviewCache;

    private static class CachedPreview {
        private final Long materialId;
        private final GenerateQuestionsResponse generated;

        private CachedPreview(Long materialId, GenerateQuestionsResponse generated) {
            this.materialId = materialId;
            this.generated = generated;
        }
    }

    public AdminAssignedExamServiceImpl(
            UserRepository userRepo,
            LearningMaterialRepository materialRepo,
            ExamRepository examRepo,
            QuestionRepository questionRepo,
            ExamQuestionRepository examQuestionRepo,
            ExamAssignmentRepository assignmentRepo,
            ExamAttemptRepository attemptRepo,
            CheatingEventRepository cheatingEventRepo,
            QuestionGenerationService questionGenerationService,
            SystemSettingsService settingsService,
            MailService mailService,
            ObjectMapper om,
            @Qualifier("practicePreviewCache") Cache<String, Object> practicePreviewCache
    ) {
        this.userRepo = userRepo;
        this.materialRepo = materialRepo;
        this.examRepo = examRepo;
        this.questionRepo = questionRepo;
        this.examQuestionRepo = examQuestionRepo;
        this.assignmentRepo = assignmentRepo;
        this.attemptRepo = attemptRepo;
        this.cheatingEventRepo = cheatingEventRepo;
        this.questionGenerationService = questionGenerationService;
        this.settingsService = settingsService;
        this.mailService = mailService;
        this.om = om;
        this.practicePreviewCache = practicePreviewCache;
    }

    private void ensureAiAvailable() {
        if (!settingsService.isAiEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI hiện tại không thể sử dụng");
        }
    }

    private User requireUser(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private String previewKey(String email, String token) {
        return "ADMIN_EXAM_PREVIEW::" + email + "::" + token;
    }

    // ========================= PREVIEW =========================
    @Override
    public AdminExamPreviewResponse preview(String adminEmail, AdminExamPreviewRequest req) {
        ensureAiAvailable();
        User admin = requireUser(adminEmail);

        Long materialId = (req == null) ? null : req.getMaterialId();
        String inputText = (req == null) ? null : req.getInputText();

        boolean hasMaterial = materialId != null;
        boolean hasText = inputText != null && !inputText.isBlank();
        if (hasMaterial == hasText) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chọn 1 trong 2: materialId hoặc inputText");
        }

        // ✅ ADMIN chọn cơ cấu MCQ/ESSAY
        QuestionMix mix = normalizeMix(req);
        int totalQuestions = mix.total();

        LearningMaterial material = resolveMaterial(admin, materialId, inputText);

        GenerateQuestionsResponse generated =
                questionGenerationService.generate(adminEmail, material.getId(), mix.mcq, mix.essay);

        if (generated == null || generated.getQuestions() == null || generated.getQuestions().size() != totalQuestions) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI response invalid (question count mismatch)");
        }

        // ✅ Extra safety: verify distribution (MCQ/ESSAY)
        long mcqGot = generated.getQuestions().stream()
                .filter(q -> q != null && q.getQuestionType() == QuestionType.MCQ)
                .count();
        long essayGot = generated.getQuestions().stream()
                .filter(q -> q != null && q.getQuestionType() == QuestionType.ESSAY)
                .count();

        if (mcqGot != mix.mcq || essayGot != mix.essay) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI response invalid (type count mismatch)");
        }

        String previewToken = UUID.randomUUID().toString();
        generated.setPreviewToken(previewToken);

        practicePreviewCache.put(previewKey(adminEmail, previewToken), new CachedPreview(material.getId(), generated));

        AdminExamPreviewResponse resp = new AdminExamPreviewResponse();
        resp.setPreviewToken(previewToken);
        resp.setTotalQuestions(totalQuestions);
        resp.setQuestions(generated.getQuestions());

        String suggested = safeTitle(generated.getExamTitle());
        if (suggested == null || suggested.isBlank()) {
            suggested = safeTitle(material.getFileName());
        }
        resp.setSuggestedTitle(suggested);

        return resp;
    }

    // ========================= CREATE + ASSIGN =========================
    @Override
    @Transactional
    public Map<String, Object> createAndAssign(String adminEmail, AdminCreateAssignedExamRequest req) {
        User admin = requireUser(adminEmail);

        String token = (req == null || req.getPreviewToken() == null) ? "" : req.getPreviewToken().trim();
        if (token.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "previewToken is required");

        String key = previewKey(adminEmail, token);

        Object rawCached = practicePreviewCache.getIfPresent(key);
        if (!(rawCached instanceof CachedPreview cached) || cached.generated == null) {
            throw new ResponseStatusException(HttpStatus.GONE, "Preview expired. Please generate preview again.");
        }

        LearningMaterial material = materialRepo.findById(cached.materialId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));

        List<GeneratedQuestionItemResponse> items = cached.generated.getQuestions();
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI response invalid (empty questions)");
        }

        // ✅ Normalize time window as VN local (no nano) to keep consistent storage + display
        LocalDateTime openAt = normalizeVn(req.getOpenAt());
        LocalDateTime dueAt = normalizeVn(req.getDueAt());
        validateTimeWindow(openAt, dueAt);

        Exam exam = new Exam();
        exam.setUser(admin);
        exam.setType(ExamType.ADMIN_ASSIGNED);

        // PassScore: vẫn theo rule hệ thống (m có thể cho admin config sau)
        exam.setPassScore(settingsService.getPassScore());

        exam.setCreatedAt(nowVn()); // ✅ VN time

        String title = safeTitle(req.getTitle());
        if (title == null || title.isBlank()) title = safeTitle(cached.generated.getExamTitle());
        exam.setTitle(title);

        // ✅ durationMinutes do ADMIN chọn (required) - KHÔNG dùng SystemSettings
        Integer durationReq = req.getDurationMinutes();
        if (durationReq == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "durationMinutes is required");
        }
        exam.setDurationMinutes(clamp(durationReq, MIN_DURATION_MINUTES, MAX_DURATION_MINUTES));

        exam = examRepo.save(exam);

        // save questions + exam_question
        for (GeneratedQuestionItemResponse item : items) {
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
            } else if (item.getQuestionType() == QuestionType.ESSAY) {
                q.setCorrectAnswer(null);
                q.setOptionsJson(null);
                q.setAnalysis(writeRubricJson(item.getSampleAnswer(), item.getKeywords(), item.getMaxScore()));
            } else {
                // nếu sau này có type mới mà AI trả về -> fail fast
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI response invalid (unsupported questionType)");
            }

            q = questionRepo.save(q);

            ExamQuestion eq = new ExamQuestion();
            eq.setExam(exam);
            eq.setQuestion(q);
            examQuestionRepo.save(eq);
        }

        // assign users
        List<Long> userIds = (req.getAssignedUserIds() == null) ? List.of() : req.getAssignedUserIds();
        userIds = userIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (userIds.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assignedUserIds is required");

        List<User> students = userRepo.findAllById(userIds);
        if (students.size() != userIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Một số học viên không tồn tại");
        }

        // clamp override duration nếu có
        Integer durationOverride = req.getDurationMinutesOverride();
        if (durationOverride != null) {
            durationOverride = clamp(durationOverride, MIN_DURATION_MINUTES, MAX_DURATION_MINUTES);
        }

        int created = 0;
        for (User s : students) {
            if (assignmentRepo.existsByExamIdAndStudentId(exam.getId(), s.getId())) continue;

            ExamAssignment asg = new ExamAssignment();
            asg.setExam(exam);
            asg.setStudent(s);
            asg.setAssignedBy(admin);

            // ✅ store VN local time as-is
            asg.setOpenAt(openAt);
            asg.setDueAt(dueAt);

            asg.setDurationMinutesOverride(durationOverride);
            asg.setStatus(AssignmentStatus.ASSIGNED);

            assignmentRepo.save(asg);
            created++;

            try {
                mailService.sendAssignedExamMail(s, exam, asg.getOpenAt(), asg.getDueAt());
            } catch (Exception ignored) {}
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("examId", exam.getId());
        out.put("assignedCount", created);
        out.put("message", "Tạo bài kiểm tra và gán học viên thành công");

        practicePreviewCache.invalidate(key);
        return out;
    }

    // ========================= LIST / DETAIL / CHEATING =========================
    @Override
    public List<Map<String, Object>> listMyExams(String adminEmail) {
        User admin = requireUser(adminEmail);

        List<Exam> exams = examRepo.findAllByUserIdOrderByCreatedAtDesc(admin.getId())
                .stream()
                .filter(e -> e.getType() == ExamType.ADMIN_ASSIGNED)
                .collect(Collectors.toList());

        if (exams.isEmpty()) return List.of();

        List<Long> examIds = exams.stream().map(Exam::getId).toList();

        Map<Long, Object[]> agg = new HashMap<>();
        for (Object[] row : assignmentRepo.aggByExamIds(examIds)) {
            agg.put((Long) row[0], row);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Exam e : exams) {
            Object[] row = agg.get(e.getId());
            long assignedCount = row == null ? 0L : (Long) row[1];

            LocalDateTime openAt = row == null ? null : normalizeVn((LocalDateTime) row[2]);
            LocalDateTime dueAt  = row == null ? null : normalizeVn((LocalDateTime) row[3]);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("examId", e.getId());
            m.put("title", e.getTitle());
            m.put("durationMinutes", e.getDurationMinutes());
            m.put("passScore", e.getPassScore());
            m.put("createdAt", normalizeVn(e.getCreatedAt()));

            m.put("assignedCount", assignedCount);
            m.put("openAt", openAt);
            m.put("dueAt", dueAt);

            out.add(m);
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminExamDetailResponse getDetail(String adminEmail, Long examId) {
        User admin = requireUser(adminEmail);

        Exam exam = examRepo.findById(examId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exam not found"));

        if (!Objects.equals(exam.getUser().getId(), admin.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }
        if (exam.getType() != ExamType.ADMIN_ASSIGNED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exam is not admin assigned");
        }

        List<ExamQuestion> eqs = examQuestionRepo.findAllByExamIdFetchQuestion(examId);
        List<QuestionDetailResponse> questions = eqs.stream().map(eq -> {
            Question q = eq.getQuestion();
            QuestionDetailResponse r = new QuestionDetailResponse();
            r.setQuestionId(q.getId());
            r.setQuestionType(q.getQuestionType());
            r.setContent(q.getContent());
            r.setOptionsJson(q.getOptionsJson());
            r.setCorrectAnswer(q.getCorrectAnswer());
            r.setAnalysis(q.getAnalysis());
            return r;
        }).collect(Collectors.toList());

        List<ExamAssignment> assignments = assignmentRepo.findAllByExamIdFetchStudentAndAttempt(examId);

        List<AdminAssignmentItemResponse> assignmentResponses = assignments.stream().map(a -> {
            AdminAssignmentItemResponse r = new AdminAssignmentItemResponse();
            r.setAssignmentId(a.getId());
            r.setStudentId(a.getStudent().getId());
            r.setStudentEmail(a.getStudent().getEmail());
            r.setStudentFullName(a.getStudent().getFullName());
            r.setStatus(a.getStatus());

            r.setOpenAt(normalizeVn(a.getOpenAt()));
            r.setDueAt(normalizeVn(a.getDueAt()));

            r.setDurationMinutesOverride(a.getDurationMinutesOverride());

            r.setStartedAt(normalizeVn(a.getStartedAt()));
            r.setSubmittedAt(normalizeVn(a.getSubmittedAt()));

            r.setAttemptId(a.getAttempt() == null ? null : a.getAttempt().getId());
            return r;
        }).collect(Collectors.toList());

        AdminExamDetailResponse resp = new AdminExamDetailResponse();
        resp.setExamId(exam.getId());
        resp.setType(exam.getType());
        resp.setTitle(exam.getTitle());
        resp.setDurationMinutes(exam.getDurationMinutes());
        resp.setPassScore(exam.getPassScore());
        resp.setCreatedAt(normalizeVn(exam.getCreatedAt()));
        resp.setQuestions(questions);
        resp.setAssignments(assignmentResponses);
        return resp;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCheatingEvents(String adminEmail, Long examId) {
        User admin = requireUser(adminEmail);

        Exam exam = examRepo.findById(examId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exam not found"));

        if (!Objects.equals(exam.getUser().getId(), admin.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }

        List<CheatingEvent> events = cheatingEventRepo.findAllByExamIdFetch(examId);

        return events.stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", e.getId());
                    m.put("assignmentId", e.getAssignment().getId());
                    m.put("studentId", e.getStudent().getId());
                    m.put("studentEmail", e.getStudent().getEmail());
                    m.put("studentFullName", e.getStudent().getFullName());
                    m.put("type", e.getType());
                    m.put("metaJson", e.getMetaJson());
                    m.put("createdAt", normalizeVn(e.getCreatedAt()));
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ========================= UPDATE =========================
    @Override
    @Transactional
    public Map<String, Object> update(String adminEmail, Long examId, AdminUpdateAssignedExamRequest req) {
        User admin = requireUser(adminEmail);

        Exam exam = examRepo.findById(examId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exam not found"));

        if (!Objects.equals(exam.getUser().getId(), admin.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }

        if (req.getTitle() != null) exam.setTitle(safeTitle(req.getTitle()));
        if (req.getDurationMinutes() != null) {
            exam.setDurationMinutes(clamp(req.getDurationMinutes(), MIN_DURATION_MINUTES, MAX_DURATION_MINUTES));
        }

        LocalDateTime openAt = normalizeVn(req.getOpenAt());
        LocalDateTime dueAt = normalizeVn(req.getDueAt());
        validateTimeWindow(openAt, dueAt);

        List<ExamAssignment> current = assignmentRepo.findAllByExamId(examId);

        for (ExamAssignment a : current) {
            if (req.getOpenAt() != null || req.getDueAt() != null) {
                a.setOpenAt(openAt);
                a.setDueAt(dueAt);
            }
            if (req.getDurationMinutesOverride() != null) {
                a.setDurationMinutesOverride(clamp(req.getDurationMinutesOverride(), MIN_DURATION_MINUTES, MAX_DURATION_MINUTES));
            }
        }

        int added = 0;
        int removed = 0;

        if (req.getAssignedUserIds() != null) {
            List<Long> targetIds = req.getAssignedUserIds().stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            Set<Long> currentIds = current.stream()
                    .map(a -> a.getStudent().getId())
                    .collect(Collectors.toSet());

            Set<Long> target = new HashSet<>(targetIds);

            for (ExamAssignment a : current) {
                if (!target.contains(a.getStudent().getId()) && a.getStatus() == AssignmentStatus.ASSIGNED) {
                    cheatingEventRepo.deleteAllByAssignmentId(a.getId());
                    assignmentRepo.delete(a);
                    removed++;
                }
            }

            List<Long> addIds = targetIds.stream()
                    .filter(id -> !currentIds.contains(id))
                    .collect(Collectors.toList());

            if (!addIds.isEmpty()) {
                List<User> students = userRepo.findAllById(addIds);
                for (User s : students) {
                    ExamAssignment asg = new ExamAssignment();
                    asg.setExam(exam);
                    asg.setStudent(s);
                    asg.setAssignedBy(admin);

                    asg.setOpenAt(openAt);
                    asg.setDueAt(dueAt);

                    Integer durationOverride = req.getDurationMinutesOverride();
                    if (durationOverride != null) {
                        durationOverride = clamp(durationOverride, MIN_DURATION_MINUTES, MAX_DURATION_MINUTES);
                    }
                    asg.setDurationMinutesOverride(durationOverride);

                    asg.setStatus(AssignmentStatus.ASSIGNED);

                    assignmentRepo.save(asg);
                    added++;

                    try {
                        mailService.sendAssignedExamMail(s, exam, asg.getOpenAt(), asg.getDueAt());
                    } catch (Exception ignored) {}
                }
            }
        }

        examRepo.save(exam);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("examId", exam.getId());
        out.put("added", added);
        out.put("removed", removed);
        out.put("message", "Cập nhật bài kiểm tra thành công");
        return out;
    }

    // ========================= HARD DELETE =========================
    @Override
    @Transactional
    public Map<String, Object> hardDelete(String adminEmail, Long examId) {
        User admin = requireUser(adminEmail);

        Exam exam = examRepo.findById(examId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exam not found"));

        if (!Objects.equals(exam.getUser().getId(), admin.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }

        List<ExamAssignment> assignments = assignmentRepo.findAllByExamId(examId);
        List<Long> assignmentIds = assignments.stream()
                .map(ExamAssignment::getId)
                .toList();

        assignmentRepo.detachAttemptsByExamId(examId);

        if (!assignmentIds.isEmpty()) {
            cheatingEventRepo.deleteAllByAssignmentIds(assignmentIds);
        }

        assignmentRepo.deleteAllByExamId(examId);
        attemptRepo.deleteAllByExamId(examId);
        examQuestionRepo.deleteAllByExamId(examId);
        examRepo.deleteById(examId);

        return Map.of(
                "message", "Xóa bài kiểm tra thành công",
                "examId", examId
        );
    }

    // ========================= HELPERS =========================

    private static final class QuestionMix {
        final int mcq;
        final int essay;
        QuestionMix(int mcq, int essay) { this.mcq = mcq; this.essay = essay; }
        int total() { return mcq + essay; }
    }

    private QuestionMix normalizeMix(AdminExamPreviewRequest req) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is required");
        }

        Integer mcqRaw = req.getMcqCount();
        Integer essayRaw = req.getEssayCount();

        // ✅ bắt buộc theo yêu cầu mới
        if (mcqRaw == null || essayRaw == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mcqCount and essayCount are required");
        }

        int mcq = mcqRaw;
        int essay = essayRaw;

        if (mcq < 0 || essay < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mcqCount/essayCount must be >= 0");
        }

        int total = mcq + essay;
        if (total < 1 || total > MAX_QUESTIONS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Total questions must be 1.." + MAX_QUESTIONS);
        }

        return new QuestionMix(mcq, essay);
    }

    private LearningMaterial resolveMaterial(User owner, Long materialId, String inputText) {
        if (materialId != null) {
            LearningMaterial material = materialRepo.findById(materialId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));

            if (material.getUser() == null || !Objects.equals(material.getUser().getId(), owner.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Material not owned by you");
            }
            if (material.getStatus() != MaterialStatus.EXTRACTED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Material is not extracted yet");
            }
            return material;
        }

        String raw = inputText == null ? "" : inputText.trim();
        if (raw.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "inputText is empty");
        if (raw.length() > MAX_TEXT_CHARS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nội dung quá dài (tối đa " + MAX_TEXT_CHARS + " ký tự)"
            );
        }

        LearningMaterial m = new LearningMaterial();
        m.setUser(owner);
        m.setFileName("Admin Pasted Text");
        m.setFileSize((long) raw.length());
        m.setFileType(com.be_ai_learning_platform.entity.enums.FileType.TEXT);
        m.setExtractedText(raw);
        m.setStatus(MaterialStatus.EXTRACTED);
        return materialRepo.save(m);
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private void validateTimeWindow(LocalDateTime openAt, LocalDateTime dueAt) {
        if (openAt != null && dueAt != null && openAt.isAfter(dueAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "openAt must be <= dueAt");
        }
    }

    private String safeTitle(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() > MAX_TITLE_CHARS) t = t.substring(0, MAX_TITLE_CHARS);
        return t;
    }

    private String safeTrim(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() > max) t = t.substring(0, max);
        return t;
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

    private String writeOptionsJson(Object rawOptions) {
        try {
            List<String> safe = normalizeOptions(rawOptions);
            return om.writeValueAsString(safe);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize optionsJson");
        }
    }

    private List<String> normalizeOptions(Object rawOptions) {
        if (rawOptions == null) return List.of();

        if (rawOptions instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object x : list) {
                if (x == null) continue;

                if (x instanceof String s) {
                    String t = s.trim();
                    if (!t.isBlank()) out.add(t);
                    continue;
                }

                if (x instanceof Map<?, ?> m) {
                    out.addAll(extractTextFromMap(m));
                    continue;
                }

                String t = String.valueOf(x).trim();
                if (!t.isBlank()) out.add(t);
            }
            return out.stream().limit(10).collect(Collectors.toList());
        }

        if (rawOptions instanceof Map<?, ?> map) {
            List<String> out = extractTextFromMap(map);
            return out.stream().limit(10).collect(Collectors.toList());
        }

        String t = String.valueOf(rawOptions).trim();
        if (t.isBlank()) return List.of();
        return List.of(t);
    }

    private List<String> extractTextFromMap(Map<?, ?> map) {
        List<String> out = new ArrayList<>();
        List<String> keyPriority = List.of("A", "B", "C", "D");

        Map<String, Object> as = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() == null) continue;
            as.put(String.valueOf(e.getKey()), e.getValue());
        }

        for (String k : keyPriority) {
            if (as.containsKey(k)) {
                Object vObj = as.get(k);
                String v = vObj == null ? "" : String.valueOf(vObj).trim();
                if (!v.isBlank()) out.add(v);
            }
        }

        for (Map.Entry<String, Object> e : as.entrySet()) {
            if (keyPriority.contains(e.getKey())) continue;
            Object vObj = e.getValue();
            if (vObj == null) continue;
            String v = String.valueOf(vObj).trim();
            if (!v.isBlank()) out.add(v);
        }

        if (out.isEmpty()) {
            for (String field : List.of("text", "value", "content", "option", "label")) {
                Object vObj = as.get(field);
                if (vObj == null) continue;
                String v = String.valueOf(vObj).trim();
                if (!v.isBlank()) out.add(v);
            }
        }

        return out;
    }

    private String writeRubricJson(String sampleAnswer, List<String> keywords, Integer maxScore) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sampleAnswer", safeTrim(sampleAnswer, 3000));
        m.put("keywords", keywords == null ? List.of() : keywords);
        m.put("maxScore", maxScore == null ? 10 : maxScore);
        try {
            return om.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}