package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.entity.LearningModule;
import com.be_ai_learning_platform.repository.ModuleRepository;
import com.be_ai_learning_platform.service.ModuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModuleServiceImpl implements ModuleService {

    private final ModuleRepository moduleRepository;

    @Override
    public List<LearningModule> getAllModules() {
        return moduleRepository.findAll();
    }
}
