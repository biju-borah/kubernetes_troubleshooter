package com.k8stroubleshooter.worker_service.llm.dto;

import java.util.List;

public record ChatRequest(List<Message>messages,String model){

}
