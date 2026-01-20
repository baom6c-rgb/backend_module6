package com.example.studentmanagement.controller;

import com.example.studentmanagement.entity.ClassEntity;
import com.example.studentmanagement.repository.ClassRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/classes")
@CrossOrigin
@RequiredArgsConstructor
public class ClassController {

    private final ClassRepository classRepository;

    @GetMapping
    public List<ClassEntity> getAll() {
        return classRepository.findAll();
    }
}
