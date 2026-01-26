package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.entity.ClassEntity;
import com.be_ai_learning_platform.repository.ClassRepository;
import com.be_ai_learning_platform.service.ClassService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClassServiceImpl implements ClassService {

    private final ClassRepository classRepository;

    @Override
    public List<ClassEntity> getAllClasses() {
        return classRepository.findAll();
    }
}
