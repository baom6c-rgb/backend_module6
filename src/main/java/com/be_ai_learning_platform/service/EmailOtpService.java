package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.response.EmailOtpResponse;

public interface EmailOtpService {

    EmailOtpResponse requestOtp(String email);

    void verifyOtp(String otpSessionId, String email, String otp);

    /**
     * Verify (if not verified) and consume OTP for REGISTER.
     * REGISTER should always call this server-side before creating user.
     */
    void verifyAndConsumeForRegister(String otpSessionId, String email, String otp);
}
