package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.request.TaskAssistantRequest;
import iuh.fit.se.nextalk_be.dto.response.TaskAssistantResponse;

public interface TaskAssistantService {
    TaskAssistantResponse ask(TaskAssistantRequest request);
    TaskAssistantResponse confirm(String confirmationId);
    TaskAssistantResponse reject(String confirmationId);
}
