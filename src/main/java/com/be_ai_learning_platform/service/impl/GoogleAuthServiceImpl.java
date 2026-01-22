package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.entity.Role;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.UserRole;
import com.be_ai_learning_platform.entity.enums.LoginProvider;
import com.be_ai_learning_platform.entity.enums.RegisterMethod;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.repository.RoleRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.repository.UserRoleRepository;
import com.be_ai_learning_platform.service.GoogleAuthService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;

@Service
@RequiredArgsConstructor
@Transactional
public class GoogleAuthServiceImpl implements GoogleAuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    private static final String CLIENT_ID =
            "317150227283-gojk42k4ohj7kgb6kadb2n6dd7sbj50p.apps.googleusercontent.com";

    @Override
    public User authenticate(String idTokenString) {

        if (idTokenString == null || idTokenString.isBlank()) {
            throw new RuntimeException("Google idToken is missing");
        }

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
        )
                .setAudience(Collections.singletonList(CLIENT_ID))
                .build();

        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(idTokenString);
        } catch (Exception e) {
            throw new RuntimeException("Google token verification failed");
        }

        if (idToken == null) {
            throw new RuntimeException("Invalid or expired Google token");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();

        String email = payload.getEmail();
        String googleId = payload.getSubject();
        String fullName = (String) payload.get("name");
        String avatarUrl = (String) payload.get("picture");

        if (email == null || email.isBlank()) {
            throw new RuntimeException("Google account has no email");
        }

        User user = userRepository.findByEmail(email).orElse(null);

        // ================== NEW USER ==================
        if (user == null) {

            user = new User();
            user.setEmail(email);
            user.setFullName(fullName);
            user.setAvatarUrl(avatarUrl);

            user.setRegisterMethod(RegisterMethod.GOOGLE);
            user.setLoginProvider(LoginProvider.GOOGLE);
            user.setProviderId(googleId);

            // 🔥 CHỈ CREATED
            user.setStatus(UserStatus.CREATED);

            user.setCreatedAt(LocalDateTime.now());
            user.setLastLoginAt(LocalDateTime.now());

            userRepository.save(user);

            Role role = roleRepository.findByName("STUDENT")
                    .orElseThrow(() -> new RuntimeException("Role STUDENT not found"));

            UserRole ur = new UserRole();
            ur.setUser(user);
            ur.setRole(role);
            userRoleRepository.save(ur);
        }
        // ================== EXISTING USER ==================
        else {

            if (user.getStatus() == UserStatus.BLOCKED) {
                throw new RuntimeException("Account is blocked");
            }

            if (user.getLoginProvider() != LoginProvider.GOOGLE) {
                throw new RuntimeException("Account registered with another method");
            }

            user.setLastLoginAt(LocalDateTime.now());
        }

        return user;
    }
}
