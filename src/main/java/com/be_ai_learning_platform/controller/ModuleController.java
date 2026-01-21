package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.entity.LearningModule;
import com.be_ai_learning_platform.repository.ModuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/modules")
@CrossOrigin
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleRepository moduleRepository;

    @GetMapping
    public List<LearningModule> getAll() {
        return moduleRepository.findAll();
    }
}
