package iuh.fit.se.nextalk_be.group;

import iuh.fit.se.nextalk_be.conversation.Conversation;
import iuh.fit.se.nextalk_be.conversation.ConversationRepository;
import iuh.fit.se.nextalk_be.conversation.ConversationType;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.exception.UnauthorizedException;
import iuh.fit.se.nextalk_be.group.dto.*;
import iuh.fit.se.nextalk_be.message.Message;
import iuh.fit.se.nextalk_be.message.MessageRepository;
import iuh.fit.se.nextalk_be.message.MessageType;
import iuh.fit.se.nextalk_be.message.dto.MessageResponse;
import iuh.fit.se.nextalk_be.notification.NotificationService;
import iuh.fit.se.nextalk_be.notification.NotificationType;
import iuh.fit.se.nextalk_be.user.User;
import iuh.fit.se.nextalk_be.user.UserRepository;
import iuh.fit.se.nextalk_be.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final NotificationService notificationService;
    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // @Transactional
    public GroupResponse createGroup(iuh.fit.se.nextalk_be.group.dto.CreateGroupRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();

        // Create a GROUP conversation to hold messages
        Conversation conversation = Conversation.builder()
                .type(ConversationType.GROUP)
                .name(request.getName())
                .owner(currentUser)
                .members(new HashSet<>())
                .build();

        // Create group entity
        Group group = Group.builder()
                .name(request.getName())
                .owner(currentUser)
                .build();

        // Collect members: owner + requested member IDs
        Set<User> conversationMembers = new HashSet<>();
        conversationMembers.add(currentUser);

        List<GroupMember> groupMembers = new ArrayList<>();
        groupMembers.add(GroupMember.builder()
                .group(group)
                .user(currentUser)
                .role(GroupRole.OWNER)
                .build());

        if (request.getMemberIds() != null) {
            for (String memberId : request.getMemberIds()) {
                if (memberId.equals(currentUser.getId())) continue;
                User member = userRepository.findById(memberId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + memberId));
                conversationMembers.add(member);
                groupMembers.add(GroupMember.builder()
                        .group(group)
                        .user(member)
                        .role(GroupRole.MEMBER)
                        .build());
            }
        }

        conversation.setMembers(conversationMembers);
        Conversation savedConversation = conversationRepository.save(conversation);

        group.setConversation(savedConversation);
        Group savedGroup = groupRepository.save(group);

        groupMemberRepository.saveAll(groupMembers);

        // Notify all added members (except the owner/creator)
        if (request.getMemberIds() != null) {
            for (String memberId : request.getMemberIds()) {
                if (memberId.equals(currentUser.getId())) continue;
                userRepository.findById(memberId).ifPresent(member ->
                        notificationService.createAndSend(
                                member,
                                NotificationType.GROUP_INVITE,
                                currentUser.getUsername() + " đã thêm bạn vào nhóm " + savedGroup.getName(),
                                savedGroup.getId().toString()
                        )
                );
            }
        }

        return mapToGroupResponse(savedGroup, groupMembers);
    }

    // @Transactional
    public GroupResponse updateGroup(String groupId, UpdateGroupRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = getGroupOrThrow(groupId);

        assertCanManageGroupSettings(group, currentUser);

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            group.setName(request.getName().trim());
            if (group.getConversation() != null) {
                group.getConversation().setName(request.getName().trim());
                conversationRepository.save(group.getConversation());
            }
        }
        boolean avatarChanged = false;
        if (request.getAvatarUrl() != null) {
            String avatarUrl = request.getAvatarUrl().trim();
            String nextAvatarUrl = avatarUrl.isEmpty() ? null : avatarUrl;
            avatarChanged = !Objects.equals(group.getAvatarUrl(), nextAvatarUrl);
            group.setAvatarUrl(avatarUrl.isEmpty() ? null : avatarUrl);
        }

        Group saved = groupRepository.save(group);
        if (avatarChanged && saved.getConversation() != null) {
            createAndBroadcastSystemMessage(saved.getConversation(), currentUser, "đã cập nhật ảnh đại diện nhóm.");
        }
        List<GroupMember> members = groupMemberRepository.findAllByGroupId(groupId);
        return mapToGroupResponse(saved, members);
    }

    // @Transactional
    public void deleteGroup(String groupId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = getGroupOrThrow(groupId);

        if (!getRole(group, currentUser).map(this::isLeaderRole).orElse(false)) {
            throw new UnauthorizedException("Only the group leader can delete this group");
        }

        groupMemberRepository.deleteAll(groupMemberRepository.findAllByGroupId(groupId));
        if (group.getConversation() != null) {
            conversationRepository.delete(group.getConversation());
        }
        groupRepository.delete(group);
    }

    // @Transactional
    public GroupResponse addMember(String groupId, AddMemberRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = getGroupOrThrow(groupId);

        assertCanApproveMembers(group, currentUser);

        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, request.getUserId())) {
            throw new BadRequestException("User is already a member of this group");
        }

        User newMember = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getUserId()));

        GroupMember groupMember = GroupMember.builder()
                .group(group)
                .user(newMember)
                .role(GroupRole.MEMBER)
                .build();
        groupMemberRepository.save(groupMember);

        // Also add to conversation members for message routing
        if (group.getConversation() != null) {
            group.getConversation().getMembers().add(newMember);
            conversationRepository.save(group.getConversation());
            createAndBroadcastSystemMessage(
                    group.getConversation(),
                    currentUser,
                    "đã mời " + newMember.getUsername() + " vào nhóm."
            );
        }

        // Notify the newly added member
        notificationService.createAndSend(
                newMember,
                NotificationType.GROUP_INVITE,
                currentUser.getUsername() + " đã thêm bạn vào nhóm " + group.getName(),
                group.getId().toString()
        );

        List<GroupMember> members = groupMemberRepository.findAllByGroupId(groupId);
        return mapToGroupResponse(group, members);
    }

    // @Transactional
    public void removeMember(String groupId, String userId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = getGroupOrThrow(groupId);

        // Owner cannot be removed; only owner/admin can remove others; member can remove themselves
        if (group.getOwner().getId().equals(userId)) {
            throw new BadRequestException("Cannot remove the group owner");
        }

        boolean isSelf = currentUser.getId().equals(userId);
        if (!isSelf) {
            GroupRole actorRole = getRole(group, currentUser)
                    .orElseThrow(() -> new UnauthorizedException("Only group members can perform this action"));
            GroupRole targetRole = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                    .map(GroupMember::getRole)
                    .orElseThrow(() -> new BadRequestException("User is not a member of this group"));
            if (!canRemoveMember(actorRole, targetRole)) {
                throw new UnauthorizedException("You cannot remove this member");
            }
        }

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new BadRequestException("User is not a member of this group");
        }

        groupMemberRepository.deleteByGroupIdAndUserId(groupId, userId);

        // Remove from conversation members too
        if (group.getConversation() != null) {
            group.getConversation().getMembers().removeIf(m -> m.getId().equals(userId));
            conversationRepository.save(group.getConversation());
        }
    }

    // @Transactional
    public GroupResponse updateMemberRole(String groupId, String userId, UpdateMemberRoleRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = getGroupOrThrow(groupId);
        GroupRole newRole = request.getRole();

        if (newRole == GroupRole.OWNER || newRole == GroupRole.LEADER || newRole == GroupRole.ADMIN) {
            throw new BadRequestException("Cannot assign leader role from this action");
        }
        if (group.getOwner().getId().equals(userId)) {
            throw new BadRequestException("Cannot change the group owner's role");
        }

        GroupRole actorRole = getRole(group, currentUser)
                .orElseThrow(() -> new UnauthorizedException("Only group members can perform this action"));
        if (!canManageRoles(actorRole)) {
            throw new UnauthorizedException("Only the group leader can update member roles");
        }

        GroupMember targetMembership = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new BadRequestException("User is not a member of this group"));
        GroupRole oldRole = targetMembership.getRole();
        if (oldRole == newRole) {
            List<GroupMember> members = groupMemberRepository.findAllByGroupId(groupId);
            return mapToGroupResponse(group, members);
        }
        if (!canChangeRole(actorRole, oldRole, newRole)) {
            throw new UnauthorizedException("You cannot update this member role");
        }

        targetMembership.setRole(newRole);
        groupMemberRepository.save(targetMembership);
        if (group.getConversation() != null) {
            createAndBroadcastSystemMessage(
                    group.getConversation(),
                    currentUser,
                    buildRoleChangeSystemContent(targetMembership.getUser().getUsername(), oldRole, newRole)
            );
        }

        List<GroupMember> members = groupMemberRepository.findAllByGroupId(groupId);
        return mapToGroupResponse(group, members);
    }

    // @Transactional(readOnly = true)
    public GroupResponse getGroupById(String groupId) {
        Group group = getGroupOrThrow(groupId);
        List<GroupMember> members = groupMemberRepository.findAllByGroupId(groupId);
        return mapToGroupResponse(group, members);
    }

    // @Transactional(readOnly = true)
    public List<GroupResponse> getMyGroups() {
        User currentUser = userService.getCurrentAuthenticatedUser();
        List<GroupMember> memberships = groupMemberRepository.findAllByUserId(currentUser.getId());
        Set<String> groupIds = memberships.stream()
                .map(m -> m.getGroup().getId())
                .collect(Collectors.toSet());
        List<Group> groups = groupRepository.findAllByOwnerIdOrIdIn(currentUser.getId(), groupIds);
        return groups.stream().map(g -> {
            List<GroupMember> members = groupMemberRepository.findAllByGroupId(g.getId());
            return mapToGroupResponse(g, members);
        }).collect(Collectors.toList());
    }

    // --- Private helpers ---

    private Group getGroupOrThrow(String groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));
    }

    private void assertCanManageGroupSettings(Group group, User user) {
        if (!getRole(group, user).map(this::isLeaderRole).orElse(false)) {
            throw new UnauthorizedException("Only the group leader can perform this action");
        }
    }

    private void assertCanApproveMembers(Group group, User user) {
        if (!getRole(group, user).map(this::canApproveMembers).orElse(false)) {
            throw new UnauthorizedException("Only the group leader or deputy can perform this action");
        }
    }

    private Optional<GroupRole> getRole(Group group, User user) {
        if (group.getOwner().getId().equals(user.getId())) {
            return Optional.of(GroupRole.OWNER);
        }
        return groupMemberRepository.findByGroupIdAndUserId(group.getId(), user.getId())
                .map(GroupMember::getRole);
    }

    private boolean isLeaderRole(GroupRole role) {
        return role == GroupRole.OWNER || role == GroupRole.LEADER || role == GroupRole.ADMIN;
    }

    private boolean canApproveMembers(GroupRole role) {
        return isLeaderRole(role) || role == GroupRole.DEPUTY;
    }

    private boolean canManageRoles(GroupRole role) {
        return isLeaderRole(role);
    }

    private boolean canChangeRole(GroupRole actorRole, GroupRole targetRole, GroupRole newRole) {
        if (isLeaderRole(actorRole)) {
            return !isLeaderRole(targetRole)
                    && (newRole == GroupRole.DEPUTY || newRole == GroupRole.MEMBER);
        }
        return false;
    }

    private boolean canRemoveMember(GroupRole actorRole, GroupRole targetRole) {
        if (isLeaderRole(actorRole)) {
            return !isLeaderRole(targetRole);
        }
        if (actorRole == GroupRole.DEPUTY) {
            return targetRole == GroupRole.MEMBER;
        }
        return false;
    }

    private String buildRoleChangeSystemContent(String username, GroupRole oldRole, GroupRole newRole) {
        if (newRole == GroupRole.DEPUTY) {
            return "đã bổ nhiệm " + username + " làm Phó nhóm.";
        }
        if (oldRole == GroupRole.DEPUTY && newRole == GroupRole.MEMBER) {
            return "đã bãi nhiệm " + username + " khỏi vai trò Phó nhóm.";
        }
        return "đã cập nhật quyền của " + username + " thanh " + roleLabel(newRole) + ".";
    }

    private String roleLabel(GroupRole role) {
        return switch (role) {
            case OWNER, LEADER, ADMIN -> "Trưởng nhóm";
            case DEPUTY -> "Phó nhóm";
            case MEMBER -> "Thành viên";
        };
    }

    private GroupResponse mapToGroupResponse(Group group, List<GroupMember> members) {
        List<GroupMemberResponse> memberResponses = members.stream()
                .map(m -> GroupMemberResponse.builder()
                        .userId(m.getUser().getId())
                        .username(m.getUser().getUsername())
                        .avatarUrl(m.getUser().getAvatarUrl())
                        .role(m.getRole().name())
                        .build())
                .collect(Collectors.toList());

        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .avatarUrl(group.getAvatarUrl())
                .ownerId(group.getOwner().getId())
                .ownerUsername(group.getOwner().getUsername())
                .conversationId(group.getConversation() != null ? group.getConversation().getId() : null)
                .members(memberResponses)
                .memberCount(memberResponses.size())
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .build();
    }

    private void createAndBroadcastSystemMessage(Conversation conversation, User actor, String content) {
        Message systemMessage = Message.builder()
                .conversation(conversation)
                .sender(actor)
                .content(content)
                .messageType(MessageType.SYSTEM)
                .build();

        Message savedSystemMessage = messageRepository.save(systemMessage);
        MessageResponse response = MessageResponse.builder()
                .id(savedSystemMessage.getId())
                .conversationId(conversation.getId())
                .senderId(actor.getId())
                .senderUsername(actor.getUsername())
                .content(savedSystemMessage.getContent())
                .messageType(savedSystemMessage.getMessageType().name())
                .attachments(List.of())
                .createdAt(savedSystemMessage.getCreatedAt())
                .statuses(List.of())
                .reactions(List.of())
                .build();

        for (User member : conversation.getMembers()) {
            messagingTemplate.convertAndSendToUser(
                    member.getUsername(),
                    "/queue/private",
                    response
            );
        }
    }
}
