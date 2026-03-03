package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.request.CheatingEventRequest;
import com.be_ai_learning_platform.entity.CheatingEvent;
import com.be_ai_learning_platform.entity.Exam;
import com.be_ai_learning_platform.entity.ExamAssignment;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.repository.CheatingEventRepository;
import com.be_ai_learning_platform.repository.ExamAssignmentRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.CheatingEventService;
import com.be_ai_learning_platform.service.mail.MailService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class CheatingEventServiceImpl implements CheatingEventService {

    private static final int META_MAX_CHARS = 2000;
    private static final int MAIL_THROTTLE_SECONDS = 60;

    private final UserRepository userRepo;
    private final ExamAssignmentRepository assignmentRepo;
    private final CheatingEventRepository cheatingRepo;
    private final MailService mailService;
    private final ObjectMapper om;

    public CheatingEventServiceImpl(
            UserRepository userRepo,
            ExamAssignmentRepository assignmentRepo,
            CheatingEventRepository cheatingRepo,
            MailService mailService,
            ObjectMapper om
    ) {
        this.userRepo = userRepo;
        this.assignmentRepo = assignmentRepo;
        this.cheatingRepo = cheatingRepo;
        this.mailService = mailService;
        this.om = om;
    }

    private User requireMe(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    @Override
    @Transactional
    public Map<String, Object> report(String studentEmail, Long assignmentId, CheatingEventRequest req) {
        User student = requireMe(studentEmail);

        if (assignmentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assignmentId is required");
        }
        if (req == null || req.getType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type is required");
        }

        ExamAssignment asg = assignmentRepo.findByIdAndStudentId(assignmentId, student.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));

        CheatingEvent ev = new CheatingEvent();
        ev.setAssignment(asg);
        ev.setStudent(student);
        ev.setType(req.getType());
        ev.setMetaJson(toJsonSafe(req.getMeta()));
        cheatingRepo.save(ev);

        // throttle mail: 1 email / 60s / assignment
        LocalDateTime after = LocalDateTime.now().minusSeconds(MAIL_THROTTLE_SECONDS);
        long recent = cheatingRepo.countByAssignmentIdAndCreatedAtAfter(assignmentId, after);
        if (recent <= 1) {
            try {
                Exam exam = asg.getExam();
                mailService.sendCheatingAlertMail(
                        asg.getAssignedBy(),
                        student,
                        exam,
                        req.getType().name(),
                        ev.getCreatedAt()
                );
            } catch (Exception ignored) {
            }
        }

        return Map.of("ok", true);
    }

    private String toJsonSafe(Object meta) {
        if (meta == null) return null;
        try {
            String json = om.writeValueAsString(meta);
            if (json.length() > META_MAX_CHARS) json = json.substring(0, META_MAX_CHARS);
            return json;
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}