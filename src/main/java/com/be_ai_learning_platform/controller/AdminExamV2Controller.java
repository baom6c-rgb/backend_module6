package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.*;
import com.be_ai_learning_platform.dto.response.GeneratePracticeSessionResponse;
import com.be_ai_learning_platform.dto.response.StartPracticeSessionResponse;
import com.be_ai_learning_platform.entity.Exam;
import com.be_ai_learning_platform.entity.ExamAssignment;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.repository.ExamAssignmentRepository;
import com.be_ai_learning_platform.repository.ExamRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.PracticeService;
import com.be_ai_learning_platform.service.mail.MailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Admin V2 exam builder:
 * - Uses EXACT same V2 endpoints/DTOs as Practice V2 for generating questions from:
 *   + upload file (materialId) OR paste text (inputText)
 * - Admin will NOT do the quiz.
 * - Instead: create exam from sessionToken and assign to one/many students.
 */
@RestController
@RequestMapping("/api/admin/exams/v2")
@RequiredArgsConstructor
public class AdminExamV2Controller {

    private final PracticeService practiceService;

    private final ExamRepository examRepo;
    private final ExamAssignmentRepository assignmentRepo;
    private final UserRepository userRepo;
    private final MailService mailService;

    // ===== same as Practice V2 =====

    @PostMapping("/generate")
    public ResponseEntity<GeneratePracticeSessionResponse> generate(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody GeneratePracticeSessionRequest req
    ) {
        return ResponseEntity.ok(practiceService.generateSessionV2(email, req));
    }

    @PostMapping("/select-topic")
    public ResponseEntity<GeneratePracticeSessionResponse> selectTopic(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody SelectTopicRequest req
    ) {
        return ResponseEntity.ok(practiceService.selectTopicAndGenerateSessionV2(email, req));
    }

    @PostMapping("/start")
    public ResponseEntity<StartPracticeSessionResponse> start(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody StartPracticeSessionRequest req
    ) {
        return ResponseEntity.ok(practiceService.startSessionV2(email, req));
    }

    @GetMapping("/sessions/{sessionToken}")
    public ResponseEntity<StartPracticeSessionResponse> getSession(
            @AuthenticationPrincipal String email,
            @PathVariable String sessionToken
    ) {
        return ResponseEntity.ok(practiceService.getSessionV2(email, sessionToken));
    }

    // ===== create exam + assign (admin-only extension) =====

    @PostMapping("/create-and-assign")
    public ResponseEntity<Map<String, Object>> createAndAssign(
            @AuthenticationPrincipal String adminEmail,
            @Valid @RequestBody AdminCreateAssignedExamFromSessionRequest req
    ) {
        User admin = userRepo.findByEmail(adminEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        Long examId = practiceService.createExamFromSessionV2(adminEmail, req.getSessionToken());

        Exam exam = examRepo.findById(examId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exam not found"));

        // optional title override
        if (req.getTitle() != null && !req.getTitle().isBlank()) {
            exam.setTitle(req.getTitle().trim());
            examRepo.save(exam);
        }

        List<Long> studentIds = req.getAssignedUserIds();
        List<Long> assignmentIds = new ArrayList<>();

        for (Long studentId : studentIds) {
            User student = userRepo.findById(studentId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found: " + studentId));

            if (assignmentRepo.existsByExamIdAndStudentId(exam.getId(), student.getId())) {
                continue; // skip duplicates
            }

            ExamAssignment ea = new ExamAssignment();
            ea.setExam(exam);
            ea.setStudent(student);
            ea.setAssignedBy(admin);

            ea.setOpenAt(req.getOpenAt());
            ea.setDueAt(req.getDueAt());
            ea.setDurationMinutesOverride(req.getDurationMinutesOverride());

            ea.setStatus(com.be_ai_learning_platform.entity.enums.AssignmentStatus.ASSIGNED);
            ea.setCreatedAt(LocalDateTime.now());

            ea = assignmentRepo.save(ea);
            assignmentIds.add(ea.getId());

            boolean sendEmail = req.getSendEmail() == null || Boolean.TRUE.equals(req.getSendEmail());
            if (sendEmail) {
                mailService.sendAssignedExamMail(student, exam, ea.getOpenAt(), ea.getDueAt());
            }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("examId", exam.getId());
        res.put("assignmentIds", assignmentIds);
        return ResponseEntity.ok(res);
    }
}
