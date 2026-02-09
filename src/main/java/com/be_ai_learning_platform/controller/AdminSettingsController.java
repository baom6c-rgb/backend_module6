package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.UpdateSystemSettingsRequest;
import com.be_ai_learning_platform.dto.request.UpdateAiSettingsRequest;
import com.be_ai_learning_platform.dto.response.AiSettingsResponse;
import com.be_ai_learning_platform.dto.response.SystemSettingsResponse;
import com.be_ai_learning_platform.service.SystemSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final SystemSettingsService settingsService;

    @GetMapping
    public ResponseEntity<SystemSettingsResponse> get() {
        return ResponseEntity.ok(settingsService.get());
    }

    @PutMapping
    public ResponseEntity<SystemSettingsResponse> update(
            @Valid @RequestBody UpdateSystemSettingsRequest req
    ) {
        return ResponseEntity.ok(settingsService.update(req));
    }

    // ===== Tab: Model AI =====

    @GetMapping("/ai")
    public ResponseEntity<AiSettingsResponse> getAiSettings() {
        return ResponseEntity.ok(settingsService.getAi());
    }

    @PutMapping("/ai")
    public ResponseEntity<AiSettingsResponse> updateAiSettings(
            @Valid @RequestBody UpdateAiSettingsRequest req
    ) {
        return ResponseEntity.ok(settingsService.updateAi(req));
    }
}
