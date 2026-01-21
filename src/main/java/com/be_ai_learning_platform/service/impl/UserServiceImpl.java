package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.entity.ClassEntity;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.repository.ClassRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final ClassRepository classRepository;

    @Override
    public void selectClass(String email, Long classId) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getClazz() != null) {
            throw new RuntimeException("User already selected class");
        }

        ClassEntity clazz = classRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));

        user.setClazz(clazz);
        user.setStatus(UserStatus.WAITING_APPROVAL);

        userRepository.save(user);
    }
}
