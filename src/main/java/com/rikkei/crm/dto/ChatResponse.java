package com.rikkei.crm.dto;

import java.time.LocalDateTime;

public record ChatResponse(
        String conversationId,
        String answer,
        LocalDateTime timestamp
) {
    public static ChatResponse of(String conversationId, String answer) {
        return new ChatResponse(conversationId, answer, LocalDateTime.now());
    }
}
