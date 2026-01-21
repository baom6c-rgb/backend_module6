package com.be_ai_learning_platform.service.mail;

import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${admin.email}")
    private String adminEmail;

    @Value("${app.backend-url}")
    private String backendUrl;

    /**
     * Chỉ dùng cho REGISTER FORM (WAITING_APPROVAL)
     * TUYỆT ĐỐI không gọi cho Google login
     */
    public void notifyWaitingApproval(User user) {

        // ===== HARD VALIDATION – sai là nổ =====
        if (user == null) {
            throw new IllegalArgumentException("User is null");
        }

        if (user.getStatus() != UserStatus.WAITING_APPROVAL) {
            throw new IllegalStateException(
                    "Không gửi mail khi status = " + user.getStatus()
            );
        }

        if (user.getApproveToken() == null || user.getApproveToken().isBlank()) {
            throw new IllegalStateException("Approve token is null or empty");
        }

        String approveLink =
                backendUrl + "/admin/approve?token=" + user.getApproveToken();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(adminEmail);
        message.setSubject("📢 Học viên chờ duyệt");

        message.setText(
                "Có học viên mới cần duyệt:\n\n" +
                        "👤 Họ tên: " + user.getFullName() + "\n" +
                        "📧 Email: " + user.getEmail() + "\n\n" +
                        "👉 Phê duyệt tại đây:\n" +
                        approveLink + "\n\n" +
                        "Trạng thái: WAITING_APPROVAL"
        );

        mailSender.send(message);
    }
}
