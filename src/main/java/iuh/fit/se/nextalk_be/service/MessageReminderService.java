package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.request.CreateMessageReminderRequest;
import iuh.fit.se.nextalk_be.dto.response.MessageReminderResponse;

import java.util.List;

public interface MessageReminderService {
    List<MessageReminderResponse> getMyReminders();

    MessageReminderResponse createReminder(CreateMessageReminderRequest request);

    MessageReminderResponse deleteReminder(String reminderId);

    MessageReminderResponse markReminderFired(String reminderId);

    void dispatchDueReminders();
}
