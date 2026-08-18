package com.rikkei.crm.dto;

public record ChatRequest(
        String conversationId,
        String message
) {
}
