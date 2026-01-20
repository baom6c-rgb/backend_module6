package com.example.studentmanagement.service;

import com.example.studentmanagement.dto.LoginRequest;
import com.example.studentmanagement.dto.RegisterRequest;
import com.example.studentmanagement.entity.ClassEntity;
import com.example.studentmanagement.entity.User;
import com.example.studentmanagement.enums.AuthProvider;
import com.example.studentmanagement.enums.UserRole;
import com.example.studentmanagement.enums.UserStatus;
import com.example.studentmanagement.repository.ClassRepository;
import com.example.studentmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ClassRepository classRepository;
    private final PasswordEncoder passwordEncoder;

    // ======================= REGISTER =======================
    public void register(RegisterRequest request) {

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        ClassEntity clazz = classRepository.findById(request.getClassId())
                .orElseThrow(() -> new RuntimeException("Class not found"));

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setClassEntity(clazz);

        // 🔥 NGHIỆP VỤ
        user.setProvider(AuthProvider.LOCAL);
        user.setRole(UserRole.STUDENT);
        user.setStatus(UserStatus.PENDING);   // ⏳ chờ admin duyệt
        user.setEnabled(true);

        userRepository.save(user);
    }

    // ======================= AUTHENTICATE =======================
    public User authenticate(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        // ❌ TÀI KHOẢN GOOGLE KHÔNG ĐƯỢC LOGIN BẰNG PASSWORD
        if (user.getProvider() == AuthProvider.GOOGLE) {
            throw new RuntimeException("Please login with Google");
        }

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )) {
            throw new RuntimeException("Invalid email or password");
        }

        return user; // ⚠️ Controller sẽ xử lý status + JWT
    }
}
