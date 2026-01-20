package com.example.studentmanagement.service;

import com.example.studentmanagement.entity.User;
import com.example.studentmanagement.enums.AuthProvider;
import com.example.studentmanagement.enums.UserRole;
import com.example.studentmanagement.enums.UserStatus;
import com.example.studentmanagement.repository.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class GoogleAuthService {

    private final UserRepository userRepository;

    // ⚠️ CLIENT ID
    private static final String CLIENT_ID =
            "317150227283-gojk42k4ohj7kgb6kadb2n6dd7sbj50p.apps.googleusercontent.com";

    public GoogleAuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User authenticate(String idTokenString) throws Exception {

        GoogleIdTokenVerifier verifier =
                new GoogleIdTokenVerifier.Builder(
                        new NetHttpTransport(),
                        GsonFactory.getDefaultInstance()
                )
                        .setAudience(Collections.singletonList(CLIENT_ID))
                        .build();

        GoogleIdToken idToken = verifier.verify(idTokenString);

        if (idToken == null) {
            throw new RuntimeException("Invalid Google ID Token");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();

        String email = payload.getEmail();
        String googleId = payload.getSubject();
        String name = (String) payload.get("name");

        // 👉 ĐÃ TỒN TẠI → TRẢ LUÔN
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User user = new User();
                    user.setEmail(email);
                    user.setFullName(name);

                    user.setProvider(AuthProvider.GOOGLE);
                    user.setProviderId(googleId);

                    user.setRole(UserRole.STUDENT);
                    user.setStatus(UserStatus.NEW); // ✅ FIX CHÍ MẠNG

                    user.setEnabled(true);
                    user.setClassEntity(null); // 👉 bắt buộc chọn lớp

                    return userRepository.save(user);
                });
    }
}
