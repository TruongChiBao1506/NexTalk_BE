package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.request.ScheduleMessageRequest;
import iuh.fit.se.nextalk_be.dto.response.ScheduledMessageResponse;

import java.util.List;

public interface ScheduledMessageService {
    ScheduledMessageResponse schedule(ScheduleMessageRequest request);
    List<ScheduledMessageResponse> getPending();
    ScheduledMessageResponse cancel(String scheduledMessageId);
    void dispatchDueMessages();
}
