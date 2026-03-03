package com.be_ai_learning_platform.dto.request;

import com.be_ai_learning_platform.entity.enums.CheatingEventType;
import jakarta.validation.constraints.NotNull;

public class CheatingEventRequest {

    @NotNull
    private CheatingEventType type;

    // JSON string/object từ FE -> BE giữ dạng string
    private Object meta;

    public CheatingEventType getType() { return type; }
    public void setType(CheatingEventType type) { this.type = type; }

    public Object getMeta() { return meta; }
    public void setMeta(Object meta) { this.meta = meta; }
}
