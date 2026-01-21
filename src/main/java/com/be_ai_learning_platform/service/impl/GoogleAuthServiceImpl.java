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
    public User authenticate(String idTokenString) throws Exception {

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
        )
                .setAudience(Collections.singletonList(CLIENT_ID))
                .build();

        GoogleIdToken idToken = verifier.verify(idTokenString);

        if (idToken == null) {
            throw new RuntimeException("Invalid Google token");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();

        String email = payload.getEmail();
        String googleId = payload.getSubject();
        String name = (String) payload.get("name");
        String avatar = (String) payload.get("picture");

        User user = userRepository.findByEmail(email).orElse(null);

        // ===================== NEW USER =====================
        if (user == null) {

            user = new User();
            user.setEmail(email);
            user.setFullName(name);
            user.setAvatarUrl(avatar);

            user.setRegisterMethod(RegisterMethod.GOOGLE);
            user.setLoginProvider(LoginProvider.GOOGLE);
            user.setProviderId(googleId);
            user.setPasswordHash(null);


            // ⭐⭐ QUAN TRỌNG
            user.setStatus(UserStatus.CREATED);
            user.setCreatedAt(LocalDateTime.now());
            user.setLastLoginAt(LocalDateTime.now());

            userRepository.save(user);

            // ⭐⭐ GÁN ROLE STUDENT
            Role studentRole = roleRepository.findByName("STUDENT")
                    .orElseThrow(() ->
                            new RuntimeException("Role STUDENT not found")
                    );

            UserRole userRole = new UserRole();
            userRole.setUser(user);
            userRole.setRole(studentRole);

            userRoleRepository.save(userRole);
        }
        // ===================== EXISTING USER =====================
        else {

            if (user.getStatus() == UserStatus.BLOCKED) {
                throw new RuntimeException("Account is blocked");
            }

            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
        }

        return user;
    }
}
