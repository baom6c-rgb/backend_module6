package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.request.CheatingEventRequest;

import java.util.Map;

public interface CheatingEventService {
    Map<String, Object> report(String studentEmail, Long assignmentId, CheatingEventRequest req);
}