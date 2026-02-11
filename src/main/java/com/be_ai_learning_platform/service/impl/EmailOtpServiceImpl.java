package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.response.EmailOtpResponse;
import com.be_ai_learning_platform.entity.EmailOtpSession;
import com.be_ai_learning_platform.repository.EmailOtpSessionRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.EmailOtpService;
import com.be_ai_learning_platform.service.mail.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class EmailOtpServiceImpl implements EmailOtpService {

    private final EmailOtpSessionRepository otpRepo;
    private final UserRepository userRepository;
    private final MailService mailService;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.otp.expire-seconds:300}")
    private int expireSeconds; // default 5 minutes

    @Value("${app.otp.cooldown-seconds:60}")
    private int cooldownSeconds; // default 60s

    @Value("${app.otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.otp.max-resend:3}")
    private int maxResend;

    @Value("${app.otp.hmac-secret:CHANGE_ME_IN_ENV}")
    private String hmacSecret;

    @Override
    public EmailOtpResponse requestOtp(String email) {
        String normalized = normalizeEmail(email);

        // If email already exists, stop early (prevent spamming registered accounts)
        if (userRepository.existsByEmail(normalized)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        // If latest session exists, enforce cooldown and max resend
        EmailOtpSession latest = otpRepo.findFirstByEmailOrderByCreatedAtDesc(normalized).orElse(null);
        if (latest != null && !latest.isConsumed() && !latest.isExpired()) {
            int remainingCooldown = remainingCooldownSeconds(latest.getLastSentAt());
            if (remainingCooldown > 0) {
                return new EmailOtpResponse(latest.getId(), remainingCooldown, remainingExpirySeconds(latest.getExpiresAt()));
            }
            if (latest.getResendCount() >= maxResend) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many OTP requests. Please try later.");
            }
        }

        String otp = generateOtp6();
        String sessionId = (latest != null && !latest.isConsumed() && !latest.isExpired())
                ? latest.getId()
                : java.util.UUID.randomUUID().toString();

        EmailOtpSession session = (latest != null && sessionId.equals(latest.getId())) ? latest : new EmailOtpSession();
        session.setId(sessionId);
        session.setEmail(normalized);

        session.setOtpHash(hmacOtp(sessionId, otp));
        session.setExpiresAt(LocalDateTime.now().plusSeconds(expireSeconds));
        session.setLastSentAt(LocalDateTime.now());
        session.setResendCount((latest != null && sessionId.equals(latest.getId())) ? latest.getResendCount() + 1 : 1);
        session.setAttempts(0);
        session.setVerifiedAt(null);
        session.setConsumedAt(null);

        otpRepo.save(session);

        mailService.sendRegisterOtp(normalized, otp, expireSeconds);

        return new EmailOtpResponse(session.getId(), cooldownSeconds, expireSeconds);
    }

    @Override
    public void verifyOtp(String otpSessionId, String email, String otp) {
        EmailOtpSession session = getSessionOrThrow(otpSessionId, email);

        if (session.isConsumed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP session already used");
        }
        if (session.isExpired()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP expired");
        }
        if (session.getAttempts() >= maxAttempts) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many OTP attempts");
        }

        boolean ok = constantTimeEquals(session.getOtpHash(), hmacOtp(session.getId(), otp));
        session.setAttempts(session.getAttempts() + 1);

        if (!ok) {
            otpRepo.save(session);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP invalid");
        }

        session.setVerifiedAt(LocalDateTime.now());
        otpRepo.save(session);
    }

    @Override
    public void verifyAndConsumeForRegister(String otpSessionId, String email, String otp) {
        EmailOtpSession session = getSessionOrThrow(otpSessionId, email);

        if (session.isConsumed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP session already used");
        }
        if (session.isExpired()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP expired");
        }

        // If not verified yet, verify now
        if (!session.isVerified()) {
            verifyOtp(otpSessionId, email, otp);
            session = getSessionOrThrow(otpSessionId, email);
        }

        // Consume
        session.setConsumedAt(LocalDateTime.now());
        otpRepo.save(session);
    }

    // ====================== helpers ======================

    private EmailOtpSession getSessionOrThrow(String otpSessionId, String email) {
        String normalized = normalizeEmail(email);
        EmailOtpSession session = otpRepo.findById(otpSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP session not found"));
        if (!normalized.equals(session.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email does not match OTP session");
        }
        return session;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String generateOtp6() {
        int n = secureRandom.nextInt(1_000_000);
        return String.format("%06d", n);
    }

    private int remainingCooldownSeconds(LocalDateTime lastSentAt) {
        if (lastSentAt == null) return 0;
        long passed = Duration.between(lastSentAt, LocalDateTime.now()).getSeconds();
        long remain = cooldownSeconds - passed;
        return (int) Math.max(0, remain);
    }

    private int remainingExpirySeconds(LocalDateTime expiresAt) {
        if (expiresAt == null) return 0;
        long remain = Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
        return (int) Math.max(0, remain);
    }

    private String hmacOtp(String sessionId, String otp) {
        // HMAC-SHA256(sessionId + ":" + otp, secret)
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal((sessionId + ":" + otp).getBytes(StandardCharsets.UTF_8));
            return toHex(out);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "OTP hashing failed");
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}
