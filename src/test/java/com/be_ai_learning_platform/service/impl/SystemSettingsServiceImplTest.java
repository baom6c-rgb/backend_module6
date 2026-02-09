package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.request.UpdateSystemSettingsRequest;
import com.be_ai_learning_platform.dto.response.SystemSettingsResponse;
import com.be_ai_learning_platform.entity.SystemSettings;
import com.be_ai_learning_platform.repository.SystemSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemSettingsServiceImplTest {

    @Mock
    private SystemSettingsRepository repo;

    @InjectMocks
    private SystemSettingsServiceImpl service;

    private SystemSettings mockSettings;

    @BeforeEach
    void setUp() {
        // Set giá trị cho các field @Value thông qua Reflection
        ReflectionTestUtils.setField(service, "defaultPassScore", 80);
        ReflectionTestUtils.setField(service, "defaultMinutesPerQuestion", 2.0);
        ReflectionTestUtils.setField(service, "defaultRetestCooldownMinutes", 30);
        ReflectionTestUtils.setField(service, "defaultEmailEnabled", true);
        ReflectionTestUtils.setField(service, "defaultAdminEmails", "admin@test.com, staff@test.com ");
        ReflectionTestUtils.setField(service, "defaultMonthlyReportEnabled", false);
        ReflectionTestUtils.setField(service, "defaultMonthlyReportDayOfMonth", 0);
        ReflectionTestUtils.setField(service, "defaultMonthlyReportTime", "23:59");
        ReflectionTestUtils.setField(service, "defaultMonthlyReportTimeZone", "Asia/Bangkok");

        mockSettings = new SystemSettings();
        mockSettings.setId(1L);
        mockSettings.setPassScore(80);
        mockSettings.setAdminEmails("admin@test.com,staff@test.com");
    }

    @Nested
    @DisplayName("Tests cho initialization (@PostConstruct)")
    class InitializationTests {

        @Test
        @DisplayName("Nên tạo settings mới nếu chưa tồn tại trong DB")
        void initIfMissing_ShouldSaveNewSettings_WhenNotExists() {
            when(repo.existsById(1L)).thenReturn(false);

            service.initIfMissing();

            ArgumentCaptor<SystemSettings> captor = ArgumentCaptor.forClass(SystemSettings.class);
            verify(repo).save(captor.capture());

            SystemSettings saved = captor.getValue();
            assertThat(saved.getPassScore()).isEqualTo(80);
            assertThat(saved.getAdminEmails()).isEqualTo("admin@test.com,staff@test.com");
            assertThat(saved.getMonthlyReportTimeZone()).isEqualTo("Asia/Bangkok");
        }

        @Test
        @DisplayName("Không làm gì nếu settings đã tồn tại")
        void initIfMissing_ShouldDoNothing_WhenAlreadyExists() {
            when(repo.existsById(1L)).thenReturn(true);

            service.initIfMissing();

            verify(repo, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Tests cho retrieval (Getters)")
    class RetrievalTests {

        @Test
        void getSettings_ShouldReturnSettings_WhenFound() {
            when(repo.findById(1L)).thenReturn(Optional.of(mockSettings));

            SystemSettings result = service.getSettings();

            assertThat(result).isNotNull();
            assertThat(result.getPassScore()).isEqualTo(80);
        }

        @Test
        void getSettings_ShouldThrowException_WhenNotFound() {
            when(repo.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getSettings())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SystemSettings not initialized");
        }

        @Test
        void getAdminEmails_ShouldReturnArrayOfStrings() {
            mockSettings.setAdminEmails(" a@b.com, b@c.com ");
            when(repo.findById(1L)).thenReturn(Optional.of(mockSettings));

            String[] emails = service.getAdminEmails();

            assertThat(emails).containsExactly("a@b.com", "b@c.com");
        }
    }

    @Nested
    @DisplayName("Tests cho Update API")
    class UpdateTests {

        private UpdateSystemSettingsRequest createValidRequest() {
            UpdateSystemSettingsRequest req = new UpdateSystemSettingsRequest();
            req.setPassScore(90);
            req.setMinutesPerQuestion(1.5);
            req.setRetestCooldownMinutes(60);
            req.setEmailNotificationsEnabled(true);
            req.setAdminEmails("new@test.com");
            req.setMonthlyReportDayOfMonth(15);
            req.setMonthlyReportTime("10:00");
            req.setMonthlyReportTimeZone("UTC");
            return req;
        }

        @Test
        @DisplayName("Cập nhật thành công với dữ liệu hợp lệ")
        void update_ShouldSuccess_WithValidData() {
            when(repo.findById(1L)).thenReturn(Optional.of(mockSettings));
            UpdateSystemSettingsRequest req = createValidRequest();

            SystemSettingsResponse response = service.update(req);

            assertThat(response.getPassScore()).isEqualTo(90);
            assertThat(response.getMonthlyReportTimeZone()).isEqualTo("UTC");
            verify(repo).save(any(SystemSettings.class));
        }

        @Test
        @DisplayName("Nên throw exception nếu passScore không hợp lệ (ví dụ > 100)")
        void update_ShouldThrowError_WhenPassScoreInvalid() {
            UpdateSystemSettingsRequest req = createValidRequest();
            req.setPassScore(101);

            assertThatThrownBy(() -> service.update(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("passScore");
        }

        @Test
        @DisplayName("Nên throw exception nếu định dạng giờ sai")
        void update_ShouldThrowError_WhenTimeFormatInvalid() {
            when(repo.findById(1L)).thenReturn(Optional.of(mockSettings));
            UpdateSystemSettingsRequest req = createValidRequest();
            req.setMonthlyReportTime("25:00"); // Sai giờ

            assertThatThrownBy(() -> service.update(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("format");
        }

        @Test
        @DisplayName("Nên throw exception nếu TimeZone không tồn tại")
        void update_ShouldThrowError_WhenTimeZoneInvalid() {
            when(repo.findById(1L)).thenReturn(Optional.of(mockSettings));
            UpdateSystemSettingsRequest req = createValidRequest();
            req.setMonthlyReportTimeZone("Mars/Base_Alpha");

            assertThatThrownBy(() -> service.update(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("invalid IANA zone id");
        }
    }

    @Test
    @DisplayName("Kiểm tra chuẩn hóa chuỗi email")
    void normalizeEmails_ShouldCleanUpString() {
        // Sử dụng Reflection để gọi private method hoặc test thông qua public method update
        UpdateSystemSettingsRequest req = new UpdateSystemSettingsRequest();
        req.setPassScore(80);
        req.setMinutesPerQuestion(2.0);
        req.setRetestCooldownMinutes(30);
        req.setAdminEmails(" test1@a.com , , test2@a.com ");

        when(repo.findById(1L)).thenReturn(Optional.of(mockSettings));

        service.update(req);

        assertThat(mockSettings.getAdminEmails()).isEqualTo("test1@a.com,test2@a.com");
    }
}