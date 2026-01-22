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

    /**
     * Gửi mail thông báo tài khoản đã được duyệt
     */
    public void notifyApprovedSuccess(User user) {

        if (user == null) {
            throw new IllegalArgumentException("User is null");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Không gửi mail khi status = " + user.getStatus()
            );
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(user.getEmail());
        message.setSubject("✅ Tài khoản đã được phê duyệt");

        message.setText(
                "Xin chào " + user.getFullName() + ",\n\n" +
                        "🎉 Tài khoản của bạn đã được quản trị viên phê duyệt thành công.\n\n" +
                        "👉 Bạn có thể đăng nhập và bắt đầu học ngay.\n\n" +
                        "Chúc bạn học tập tốt!\n\n" +
                        "— AI Learning Platform"
        );

        mailSender.send(message);
    }
    public void sendForgotPasswordMail(String email, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail); // Sử dụng giá trị từ @Value đã có
        message.setTo(email);
        message.setSubject("🔑 Đặt lại mật khẩu tài khoản AI Learning");

        message.setText(
                "Bạn nhận được email này vì đã yêu cầu đặt lại mật khẩu.\n\n" +
                        "👉 Vui lòng click vào link bên dưới để thực hiện thay đổi (link có hiệu lực trong 15 phút):\n" +
                        resetLink + "\n\n" +
                        "Nếu bạn không yêu cầu điều này, vui lòng bỏ qua email này.\n\n" +
                        "— AI Learning Platform"
        );

        mailSender.send(message);
    }
}
