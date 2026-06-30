package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.request.InviteUserRequest;
import iuh.fit.se.nextalk_be.dto.response.GroupInvitationResponse;
import iuh.fit.se.nextalk_be.entity.Group;
import iuh.fit.se.nextalk_be.entity.GroupInvitation;
import iuh.fit.se.nextalk_be.entity.GroupMember;
import iuh.fit.se.nextalk_be.entity.GroupRole;
import iuh.fit.se.nextalk_be.entity.InvitationStatus;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.exception.UnauthorizedException;
import iuh.fit.se.nextalk_be.repository.GroupInvitationRepository;
import iuh.fit.se.nextalk_be.repository.GroupMemberRepository;
import iuh.fit.se.nextalk_be.repository.GroupRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.GroupService;
import iuh.fit.se.nextalk_be.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public interface GroupInvitationService {
    public void inviteMember(String groupId, InviteUserRequest request);
    public List<GroupInvitationResponse> getMyPendingInvitations();
    public List<GroupInvitationResponse> getWaitingApprovals(String groupId);
    public void acceptInvitation(String inviteId);
    public void rejectInvitation(String inviteId);
    public void approveMember(String inviteId);
    public void declineMember(String inviteId);
}
