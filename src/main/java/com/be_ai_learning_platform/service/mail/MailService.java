package com.be_ai_learning_platform.service.mail;

import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.service.SystemSettingsService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final SystemSettingsService settingsService;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.backend-url}")
    private String backendUrl;

    // ===================== Existing mails =====================

    public void notifyWaitingApproval(User user) {

        if (user == null) throw new IllegalArgumentException("User is null");
        if (user.getStatus() != UserStatus.WAITING_APPROVAL)
            throw new IllegalStateException("Invalid status: " + user.getStatus());

        if (!settingsService.isEmailNotificationEnabled()) return;

        String[] adminEmails = settingsService.getAdminEmails();
        if (adminEmails.length == 0) return;

        String approveLink = backendUrl + "/admin/approve?token=" + user.getApproveToken();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(adminEmails);
        message.setSubject("📢 Học viên chờ duyệt");

        message.setText(
                "Có học viên mới cần duyệt:\n\n" +
                        "👤 Họ tên: " + user.getFullName() + "\n" +
                        "📧 Email: " + user.getEmail() + "\n\n" +
                        "👉 Phê duyệt tại đây:\n" +
                        approveLink
        );

        mailSender.send(message);
    }

    public void notifyApprovedSuccess(User user) {

        if (user == null) throw new IllegalArgumentException("User is null");
        if (user.getStatus() != UserStatus.ACTIVE)
            throw new IllegalStateException("Invalid status: " + user.getStatus());

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(user.getEmail());
        message.setSubject("✅ Tài khoản đã được phê duyệt");

        message.setText(
                "Xin chào " + user.getFullName() + ",\n\n" +
                        "Tài khoản của bạn đã được phê duyệt.\n" +
                        "Bạn có thể đăng nhập và học ngay.\n\n" +
                        "— AI Learning Platform"
        );

        mailSender.send(message);
    }

    public void sendForgotPasswordMail(String email, String resetLink) {

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(email);
        message.setSubject("🔑 Đặt lại mật khẩu");

        message.setText(
                "Click link sau để đặt lại mật khẩu (15 phút):\n" +
                        resetLink
        );

        mailSender.send(message);
    }

    // ===================== US21: Monthly report mail =====================

    /**
     * Gửi email báo cáo tháng cho admin:
     * - HTML body
     * - Đính kèm file Excel (attachment)
     *
     * @param recipients list admin emails
     * @param subject    email subject
     * @param htmlBody   html content
     * @param attachment excel file (can be null)
     */
    public void sendMonthlyReportEmail(List<String> recipients, String subject, String htmlBody, File attachment) {
        if (!settingsService.isEmailNotificationEnabled()) return;
        if (recipients == null || recipients.isEmpty()) return;

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    true, // multipart
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(fromEmail);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            if (attachment != null && attachment.exists() && attachment.isFile()) {
                helper.addAttachment(attachment.getName(), new FileSystemResource(attachment));
            }

            mailSender.send(mimeMessage);
        } catch (Exception e) {
            throw new RuntimeException("Send monthly report email failed", e);
        }
    }
}
