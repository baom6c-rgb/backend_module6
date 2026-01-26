package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.GenerateQuestionsRequest;
import com.be_ai_learning_platform.dto.response.GenerateQuestionsResponse;
import com.be_ai_learning_platform.service.QuestionGenerationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student/exams")
public class StudentExamController {

    private final QuestionGenerationService service;

    public StudentExamController(QuestionGenerationService service) {
        this.service = service;
    }

    @PostMapping("/generate")
    public ResponseEntity<GenerateQuestionsResponse> generate(
            Authentication authentication,
            @Valid @RequestBody GenerateQuestionsRequest req
    ) {
        String email = authentication.getName(); // email trong JWT
        return ResponseEntity.ok(service.generate(email, req.getMaterialId(), req.getNumberOfQuestions()));
    }
}
