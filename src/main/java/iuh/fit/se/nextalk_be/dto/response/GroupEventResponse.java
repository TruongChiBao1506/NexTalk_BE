package iuh.fit.se.nextalk_be.dto.response;

import iuh.fit.se.nextalk_be.entity.GroupEventRsvpStatus;
import iuh.fit.se.nextalk_be.entity.GroupEventStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupEventResponse {
    private String id;
    private String groupId;
    private String conversationId;
    private String messageId;
    private String title;
    private String description;
    private String location;
    private String meetingUrl;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private int reminderMinutes;
    private GroupEventStatus status;
    private String creatorId;
    private String creatorUsername;
    private String creatorAvatarUrl;
    private GroupEventRsvpStatus currentUserRsvp;
    private int attendingCount;
    private int maybeCount;
    private int notAttendingCount;
    private List<GroupEventParticipantResponse> participants;
    private boolean canManage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
