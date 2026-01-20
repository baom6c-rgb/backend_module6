package com.example.studentmanagement.service;

import com.example.studentmanagement.entity.ClassEntity;
import com.example.studentmanagement.entity.User;
import com.example.studentmanagement.enums.UserStatus;
import com.example.studentmanagement.repository.ClassRepository;
import com.example.studentmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ClassRepository classRepository;

    public void selectClass(String email, Long classId) {

        User user = userRepository.findByEmail(email)
                .orElseThrow();

        ClassEntity classEntity = classRepository.findById(classId)
                .orElseThrow();

        user.setClassEntity(classEntity);
        user.setStatus(UserStatus.PENDING);

        userRepository.save(user);
    }
}

