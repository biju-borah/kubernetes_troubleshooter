package com.k8stroubleshooter.worker_service.llm.dto;


public record Choice(int index, Message message, String finish_reason) {
}
