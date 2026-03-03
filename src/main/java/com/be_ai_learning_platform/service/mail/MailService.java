package com.be_ai_learning_platform.service.mail;

import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.Exam;
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
import java.time.LocalDateTime;
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

        message.setSubject("🔔 [Bumblefly AI] Yêu cầu phê duyệt tài khoản mới");

        message.setText(
                "Kính gửi Admin,\n\n" +
                        "Hệ thống Bumblefly AI phát hiện một tài khoản mới cần được phê duyệt.\n\n" +
                        "===== THÔNG TIN HỌC VIÊN =====\n" +
                        "Họ tên : " + user.getFullName() + "\n" +
                        "Email  : " + user.getEmail() + "\n\n" +
                        "===== PHÊ DUYỆT =====\n" +
                        "Truy cập đường dẫn sau để xử lý:\n" +
                        approveLink + "\n\n" +
                        "Đây là email tự động từ hệ thống Bumblefly AI.\n" +
                        "Vui lòng không trả lời email này."
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
        message.setSubject("🎉 [Bumblefly AI] Tài khoản của bạn đã được phê duyệt");

        message.setText(
                "Xin chào " + user.getFullName() + ",\n\n" +
                        "Chúc mừng! Tài khoản của bạn trên hệ thống Bumblefly AI đã được phê duyệt thành công.\n\n" +
                        "Bạn có thể đăng nhập ngay để bắt đầu học tập, luyện tập và theo dõi tiến độ của mình.\n\n" +
                        "👉 Truy cập hệ thống tại:\n" +
                        "https://your-domain.com\n\n" +
                        "Chúc bạn học tập hiệu quả!\n\n" +
                        "Trân trọng,\n" +
                        "Bumblefly AI\n\n" +
                        "———\n" +
                        "Đây là email tự động. Vui lòng không trả lời email này."
        );

        mailSender.send(message);
    }

    public void sendForgotPasswordMail(String email, String resetLink) {

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(email);
        message.setSubject("🔑 [Bumblefly AI] Yêu cầu đặt lại mật khẩu");

        message.setText(
                "Xin chào,\n\n" +
                        "Chúng tôi đã nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn trên hệ thống Bumblefly AI.\n\n" +
                        "👉 Nhấn vào liên kết bên dưới để đặt lại mật khẩu (liên kết có hiệu lực trong 15 phút):\n" +
                        resetLink + "\n\n" +
                        "Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email này. " +
                        "Mật khẩu của bạn sẽ không thay đổi.\n\n" +
                        "Trân trọng,\n" +
                        "Đội ngũ Bumblefly AI\n\n" +
                        "———\n" +
                        "Đây là email tự động. Vui lòng không trả lời email này."
        );

        mailSender.send(message);
    }


    // ===================== OTP register mail =====================

    public void sendRegisterOtp(String email, String otp, int expireSeconds) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(email);
        message.setSubject("🔐 [Bumblefly AI] Mã xác thực đăng ký");

        message.setText(
                "Xin chào,\n\n" +
                        "Mã OTP xác thực đăng ký của bạn là: " + otp + "\n\n" +
                        "Mã có hiệu lực trong " + (expireSeconds / 60) + " phút.\n\n" +
                        "Nếu bạn không yêu cầu đăng ký, vui lòng bỏ qua email này.\n\n" +
                        "Trân trọng,\n" +
                        "Bumblefly AI\n\n" +
                        "———\n" +
                        "Đây là email tự động. Vui lòng không trả lời email này."
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

    // ===================== Admin assigned exam mails =====================

    public void sendAssignedExamMail(User student, Exam exam, LocalDateTime openAt, LocalDateTime dueAt) {
        if (student == null || exam == null) return;
        if (!settingsService.isEmailNotificationEnabled()) return;

        String openText = (openAt == null) ? "Ngay bây giờ" : openAt.toString();
        String dueText = (dueAt == null) ? "Không giới hạn" : dueAt.toString();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(student.getEmail());
        message.setSubject("📝 [Bumblefly AI] Bạn được giao một bài kiểm tra mới");

        message.setText(
                "Xin chào " + safeName(student.getFullName()) + ",\n\n" +
                        "Bạn vừa được Admin giao một bài kiểm tra mới trên hệ thống Bumblefly AI.\n\n" +
                        "===== THÔNG TIN BÀI KIỂM TRA =====\n" +
                        "Tên bài: " + safeName(exam.getTitle()) + "\n" +
                        "Thời lượng: " + (exam.getDurationMinutes() == null ? "" : (exam.getDurationMinutes() + " phút")) + "\n" +
                        "Mở từ: " + openText + "\n" +
                        "Hạn nộp: " + dueText + "\n\n" +
                        "Vui lòng đăng nhập để làm bài.\n\n" +
                        "Trân trọng,\n" +
                        "Bumblefly AI\n\n" +
                        "———\n" +
                        "Đây là email tự động. Vui lòng không trả lời email này."
        );

        mailSender.send(message);
    }

    public void sendCheatingAlertMail(User admin, User student, Exam exam, String eventType, LocalDateTime at) {
        if (!settingsService.isEmailNotificationEnabled()) return;
        if (admin == null || admin.getEmail() == null || admin.getEmail().isBlank()) {
            // fallback: send to configured admins
            String[] admins = settingsService.getAdminEmails();
            if (admins.length == 0) return;

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(admins);
            message.setSubject("⚠️ [Bumblefly AI] Cảnh báo gian lận");
            message.setText(buildCheatBody(student, exam, eventType, at));
            mailSender.send(message);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(admin.getEmail());
        message.setSubject("⚠️ [Bumblefly AI] Cảnh báo gian lận");
        message.setText(buildCheatBody(student, exam, eventType, at));
        mailSender.send(message);
    }

    private String buildCheatBody(User student, Exam exam, String eventType, LocalDateTime at) {
        return "Kính gửi Admin,\n\n" +
                "Hệ thống Bumblefly AI ghi nhận một hành vi nghi ngờ gian lận trong lúc làm bài.\n\n" +
                "===== THÔNG TIN =====\n" +
                "Học viên: " + (student == null ? "" : safeName(student.getFullName())) + "\n" +
                "Email: " + (student == null ? "" : safeName(student.getEmail())) + "\n" +
                "Bài kiểm tra: " + (exam == null ? "" : safeName(exam.getTitle())) + "\n" +
                "Sự kiện: " + safeName(eventType) + "\n" +
                "Thời điểm: " + (at == null ? "" : at.toString()) + "\n\n" +
                "Bạn có thể vào trang quản trị để xem chi tiết log.\n\n" +
                "———\n" +
                "Đây là email tự động. Vui lòng không trả lời email này.";
    }

    private String safeName(String s) {
        if (s == null) return "";
        return s.trim();
    }
}
