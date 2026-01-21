package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.entity.ClassEntity;
import com.be_ai_learning_platform.service.ClassService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/classes")
@CrossOrigin(origins = "http://localhost:5175")
@RequiredArgsConstructor
public class ClassController {

    private final ClassService classService;

    @GetMapping
    public List<ClassEntity> getAll() {
        return classService.getAllClasses();
    }
}
