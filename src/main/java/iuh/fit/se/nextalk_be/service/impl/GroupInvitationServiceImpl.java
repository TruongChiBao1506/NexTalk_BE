package iuh.fit.se.nextalk_be.service.impl;
import iuh.fit.se.nextalk_be.service.GroupInvitationService;

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

@Service
@RequiredArgsConstructor
public class GroupInvitationServiceImpl implements GroupInvitationService {

    private final GroupInvitationRepository groupInvitationRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final GroupService groupService;

    public void inviteMember(String groupId, InviteUserRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, currentUser.getId()) && !group.getOwner().getId().equals(currentUser.getId())) {
            throw new UnauthorizedException("You are not a member of this group");
        }

        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, request.getUserId())) {
            throw new BadRequestException("User is already a member of this group");
        }

        User invitee = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getUserId()));

        GroupRole actorRole = getRole(group, currentUser);
        boolean isLeader = isLeaderRole(actorRole);

        if (!group.isRequiresApproval() && isLeader) {
            groupService.doAddMember(group, request.getUserId(), currentUser);
            return;
        }

        if (groupInvitationRepository.existsByGroupIdAndInviteeId(groupId, invitee.getId())) {
            throw new BadRequestException("User has already been invited to this group");
        }

        GroupInvitation invitation = GroupInvitation.builder()
                .group(group)
                .inviter(currentUser)
                .invitee(invitee)
                .status(InvitationStatus.PENDING)
                .build();
        groupInvitationRepository.save(invitation);
    }

    public List<GroupInvitationResponse> getMyPendingInvitations() {
        User currentUser = userService.getCurrentAuthenticatedUser();
        return groupInvitationRepository.findAllByInviteeIdAndStatus(currentUser.getId(), InvitationStatus.PENDING)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public List<GroupInvitationResponse> getWaitingApprovals(String groupId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));
        
        GroupRole actorRole = getRole(group, currentUser);
        if (!isLeaderRole(actorRole) && actorRole != GroupRole.DEPUTY) {
            throw new UnauthorizedException("Only leader or deputy can view waiting approvals");
        }

        return groupInvitationRepository.findAllByGroupIdAndStatus(groupId, InvitationStatus.WAITING_APPROVAL)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public void acceptInvitation(String inviteId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        GroupInvitation invitation = groupInvitationRepository.findById(inviteId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found: " + inviteId));

        if (!invitation.getInvitee().getId().equals(currentUser.getId())) {
            throw new UnauthorizedException("You cannot accept this invitation");
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BadRequestException("Invitation is not pending");
        }

        if (invitation.getGroup().isRequiresApproval()) {
            invitation.setStatus(InvitationStatus.WAITING_APPROVAL);
            groupInvitationRepository.save(invitation);
        } else {
            groupService.doAddMember(invitation.getGroup(), currentUser.getId(), invitation.getInviter());
            groupInvitationRepository.delete(invitation);
        }
    }

    public void rejectInvitation(String inviteId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        GroupInvitation invitation = groupInvitationRepository.findById(inviteId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found: " + inviteId));

        if (!invitation.getInvitee().getId().equals(currentUser.getId())) {
            throw new UnauthorizedException("You cannot reject this invitation");
        }

        groupInvitationRepository.delete(invitation);
    }

    public void approveMember(String inviteId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        GroupInvitation invitation = groupInvitationRepository.findById(inviteId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found: " + inviteId));

        Group group = invitation.getGroup();
        GroupRole actorRole = getRole(group, currentUser);
        if (!isLeaderRole(actorRole) && actorRole != GroupRole.DEPUTY) {
            throw new UnauthorizedException("Only leader or deputy can approve members");
        }

        if (invitation.getStatus() != InvitationStatus.WAITING_APPROVAL) {
            throw new BadRequestException("User has not accepted the invitation yet");
        }

        groupService.doAddMember(group, invitation.getInvitee().getId(), invitation.getInviter());
        groupInvitationRepository.delete(invitation);
    }

    public void declineMember(String inviteId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        GroupInvitation invitation = groupInvitationRepository.findById(inviteId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found: " + inviteId));

        Group group = invitation.getGroup();
        GroupRole actorRole = getRole(group, currentUser);
        if (!isLeaderRole(actorRole) && actorRole != GroupRole.DEPUTY) {
            throw new UnauthorizedException("Only leader or deputy can decline members");
        }

        groupInvitationRepository.delete(invitation);
    }

    private GroupRole getRole(Group group, User user) {
        if (group.getOwner().getId().equals(user.getId())) {
            return GroupRole.OWNER;
        }
        return groupMemberRepository.findByGroupIdAndUserId(group.getId(), user.getId())
                .map(GroupMember::getRole)
                .orElse(null);
    }

    private boolean isLeaderRole(GroupRole role) {
        return role == GroupRole.OWNER || role == GroupRole.LEADER || role == GroupRole.ADMIN;
    }

    private GroupInvitationResponse mapToResponse(GroupInvitation invitation) {
        return GroupInvitationResponse.builder()
                .id(invitation.getId())
                .groupId(invitation.getGroup().getId())
                .groupName(invitation.getGroup().getName())
                .groupAvatarUrl(invitation.getGroup().getAvatarUrl())
                .inviterId(invitation.getInviter().getId())
                .inviterUsername(invitation.getInviter().getUsername())
                .inviteeId(invitation.getInvitee().getId())
                .inviteeUsername(invitation.getInvitee().getUsername())
                .inviteeAvatarUrl(invitation.getInvitee().getAvatarUrl())
                .status(invitation.getStatus())
                .createdAt(invitation.getCreatedAt())
                .build();
    }
}
