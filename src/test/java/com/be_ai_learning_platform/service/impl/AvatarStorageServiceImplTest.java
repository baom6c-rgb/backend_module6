package com.be_ai_learning_platform.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AvatarStorageServiceImplTest {

    private AvatarStorageServiceImpl service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        service = new AvatarStorageServiceImpl();

        // inject value @Value
        ReflectionTestUtils.setField(service, "avatarDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "publicBaseUrl", "http://localhost:8080");
    }

    // ========================= SUCCESS =========================

    @Test
    void storeAvatar_success_jpeg() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpeg",
                "fake-image".getBytes()
        );

        String url = service.storeAvatar(file, 1L);

        assertNotNull(url);
        assertTrue(url.contains("/uploads/avatars/u1_"));
        assertTrue(url.endsWith(".jpeg"));

        // verify file created
        assertEquals(1, Files.list(tempDir).count());
    }

    @Test
    void storeAvatar_success_noExtension_fallbackByContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar",
                "image/png",
                "fake".getBytes()
        );

        String url = service.storeAvatar(file, 2L);

        assertTrue(url.endsWith(".png"));
    }

    // ========================= VALIDATION =========================

    @Test
    void storeAvatar_nullFile_throwException() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.storeAvatar(null, 1L));

        assertEquals("File is required", ex.getMessage());
    }

    @Test
    void storeAvatar_emptyFile_throwException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.jpg", "image/jpeg", new byte[0]);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.storeAvatar(file, 1L));

        assertEquals("File is required", ex.getMessage());
    }

    @Test
    void storeAvatar_fileTooLarge_throwException() {
        byte[] big = new byte[2 * 1024 * 1024 + 1];

        MockMultipartFile file = new MockMultipartFile(
                "file", "a.jpg", "image/jpeg", big);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.storeAvatar(file, 1L));

        assertEquals("Avatar must be <= 2MB", ex.getMessage());
    }

    @Test
    void storeAvatar_invalidContentType_throwException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.gif", "image/gif", "x".getBytes());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.storeAvatar(file, 1L));

        assertEquals("Only jpg/png/webp are allowed", ex.getMessage());
    }

    // ========================= IO ERROR =========================

    @Test
    void storeAvatar_ioException_throwRuntime() throws IOException {
        Path filePath = Files.createFile(tempDir.resolve("not-dir"));
        ReflectionTestUtils.setField(service, "avatarDir", filePath.toString());

        MockMultipartFile file = new MockMultipartFile(
                "file", "a.jpg", "image/jpeg", "x".getBytes());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.storeAvatar(file, 1L));

        assertEquals("Upload avatar failed", ex.getMessage());
    }
}
