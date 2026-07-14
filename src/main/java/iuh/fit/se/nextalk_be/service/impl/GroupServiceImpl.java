package iuh.fit.se.nextalk_be.service.impl;
import iuh.fit.se.nextalk_be.service.GroupService;

import iuh.fit.se.nextalk_be.dto.request.AddMemberRequest;
import iuh.fit.se.nextalk_be.dto.request.CreateGroupRequest;
import iuh.fit.se.nextalk_be.dto.request.UpdateGroupRequest;
import iuh.fit.se.nextalk_be.dto.request.UpdateMemberRoleRequest;
import iuh.fit.se.nextalk_be.dto.response.ChannelResponse;
import iuh.fit.se.nextalk_be.dto.response.GroupMemberResponse;
import iuh.fit.se.nextalk_be.dto.response.GroupResponse;
import iuh.fit.se.nextalk_be.dto.response.MessageResponse;
import iuh.fit.se.nextalk_be.dto.response.PublicGroupInfoResponse;
import iuh.fit.se.nextalk_be.entity.Channel;
import iuh.fit.se.nextalk_be.entity.ChannelType;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationType;
import iuh.fit.se.nextalk_be.entity.Group;
import iuh.fit.se.nextalk_be.entity.GroupInvitation;
import iuh.fit.se.nextalk_be.entity.GroupMember;
import iuh.fit.se.nextalk_be.entity.GroupRole;
import iuh.fit.se.nextalk_be.entity.InvitationStatus;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.entity.NotificationType;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.exception.UnauthorizedException;
import iuh.fit.se.nextalk_be.repository.ChannelRepository;
import iuh.fit.se.nextalk_be.repository.ChannelTaskRepository;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.GroupInvitationRepository;
import iuh.fit.se.nextalk_be.repository.GroupMemberRepository;
import iuh.fit.se.nextalk_be.repository.GroupRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.NotificationService;
import iuh.fit.se.nextalk_be.service.UserService;


import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupInvitationRepository groupInvitationRepository;
    private final ConversationRepository conversationRepository;
    private final ChannelRepository channelRepository;
    private final ChannelTaskRepository channelTaskRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final NotificationService notificationService;
    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public GroupResponse createGroup(CreateGroupRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();

        // Create group entity
        Group group = Group.builder()
                .name(request.getName())
                .owner(currentUser)
                .requiresApproval(request.isRequiresApproval())
                .inviteCode(generateUniqueInviteCode())
                .build();
        Group savedGroup = groupRepository.save(group);

        // Collect members: owner + requested member IDs
        Set<User> conversationMembers = new HashSet<>();
        conversationMembers.add(currentUser);

        List<GroupMember> groupMembers = new ArrayList<>();
        groupMembers.add(GroupMember.builder()
                .group(savedGroup)
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
                        .group(savedGroup)
                        .user(member)
                        .role(GroupRole.MEMBER)
                        .build());
            }
        }
        groupMemberRepository.saveAll(groupMembers);

        // Create default channel ("Chung")
        Conversation conversation = Conversation.builder()
                .type(ConversationType.GROUP)
                .name("Chung")
                .owner(currentUser)
                .members(conversationMembers)
                .build();
        Conversation savedConversation = conversationRepository.save(conversation);

        Channel defaultChannel = Channel.builder()
                .name("Chung")
                .type(ChannelType.TEXT)
                .isPrivate(false)
                .group(savedGroup)
                .conversation(savedConversation)
                .build();
        channelRepository.save(defaultChannel);

        // Notify all added members
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

    public GroupResponse updateGroup(String groupId, UpdateGroupRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = getGroupOrThrow(groupId);

        assertCanManageGroupSettings(group, currentUser);

        boolean nameChanged = false;
        String newName = null;
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            String trimmedName = request.getName().trim();
            if (!trimmedName.equals(group.getName())) {
                nameChanged = true;
                newName = trimmedName;
            }
            group.setName(trimmedName);
        }
        boolean avatarChanged = false;
        if (request.getAvatarUrl() != null) {
            String avatarUrl = request.getAvatarUrl().trim();
            String nextAvatarUrl = avatarUrl.isEmpty() ? null : avatarUrl;
            avatarChanged = !Objects.equals(group.getAvatarUrl(), nextAvatarUrl);
            group.setAvatarUrl(avatarUrl.isEmpty() ? null : avatarUrl);
        }

        boolean requiresApprovalChanged = false;
        if (request.getRequiresApproval() != null && request.getRequiresApproval() != group.isRequiresApproval()) {
            requiresApprovalChanged = true;
            group.setRequiresApproval(request.getRequiresApproval());
        }

        Group saved = groupRepository.save(group);
        List<Channel> channels = channelRepository.findAllByGroupId(groupId);
        if (!channels.isEmpty()) {
            if (avatarChanged) {
                createAndBroadcastSystemMessage(channels.get(0).getConversation(), currentUser, "đã cập nhật ảnh đại diện nhóm.");
            }
            if (nameChanged && newName != null) {
                createAndBroadcastSystemMessage(channels.get(0).getConversation(), currentUser, "đã đổi tên nhóm thành \"" + newName + "\".");
            }
            if (requiresApprovalChanged) {
                String actionStr = group.isRequiresApproval() ? "bật" : "tắt";
                createAndBroadcastSystemMessage(channels.get(0).getConversation(), currentUser, "đã " + actionStr + " chức năng phê duyệt thành viên.");
            }
        }
        List<GroupMember> members = groupMemberRepository.findAllByGroupId(groupId);
        return mapToGroupResponse(saved, members);
    }

    public void deleteGroup(String groupId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = getGroupOrThrow(groupId);

        if (!getRole(group, currentUser).map(this::isLeaderRole).orElse(false)) {
            throw new UnauthorizedException("Only the group leader can delete this group");
        }

        groupMemberRepository.deleteAll(groupMemberRepository.findAllByGroupId(groupId));
        groupInvitationRepository.deleteAllByGroupId(groupId);
        channelTaskRepository.deleteAllByGroupId(groupId);
        
        List<Channel> channels = channelRepository.findAllByGroupId(groupId);
        for (Channel ch : channels) {
            if (ch.getConversation() != null) {
                conversationRepository.delete(ch.getConversation());
            }
        }
        channelRepository.deleteAll(channels);
        groupRepository.delete(group);
    }

    public GroupResponse addMember(String groupId, AddMemberRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = getGroupOrThrow(groupId);

        assertCanApproveMembers(group, currentUser);

        return doAddMember(group, request.getUserId(), currentUser);
    }

    public GroupResponse doAddMember(Group group, String userId, User actor) {
        if (groupMemberRepository.existsByGroupIdAndUserId(group.getId(), userId)) {
            throw new BadRequestException("User is already a member of this group");
        }

        User newMember = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        GroupMember groupMember = GroupMember.builder()
                .group(group)
                .user(newMember)
                .role(GroupRole.MEMBER)
                .build();
        groupMemberRepository.save(groupMember);

        // Also add to conversation members of all public channels
        List<Channel> channels = channelRepository.findAllByGroupId(group.getId());
        for (Channel ch : channels) {
            if (!ch.isPrivate() && ch.getConversation() != null) {
                ch.getConversation().getMembers().add(newMember);
                conversationRepository.save(ch.getConversation());
            }
        }

        if (!channels.isEmpty() && channels.get(0).getConversation() != null) {
            String messageContent = actor.getId().equals(newMember.getId())
                    ? "đã tham gia nhóm bằng liên kết."
                    : "đã thêm " + newMember.getUsername() + " vào nhóm.";
                    
            createAndBroadcastSystemMessage(
                    channels.get(0).getConversation(),
                    actor,
                    messageContent
            );
        }

        if (!actor.getId().equals(newMember.getId())) {
            notificationService.createAndSend(
                    newMember,
                    NotificationType.GROUP_INVITE,
                    actor.getUsername() + " đã thêm bạn vào nhóm " + group.getName(),
                    group.getId().toString()
            );
        }

        List<GroupMember> members = groupMemberRepository.findAllByGroupId(group.getId());
        return mapToGroupResponse(group, members);
    }

    public void removeMember(String groupId, String userId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = getGroupOrThrow(groupId);

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

        List<Channel> channels = channelRepository.findAllByGroupId(groupId);
        for (Channel ch : channels) {
            if (ch.getConversation() != null) {
                ch.getConversation().getMembers().removeIf(m -> m.getId().equals(userId));
                conversationRepository.save(ch.getConversation());
            }
        }
    }

    public GroupResponse updateMemberRole(String groupId, String userId, UpdateMemberRoleRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = getGroupOrThrow(groupId);
        GroupRole newRole = request.getRole();

        if (newRole == GroupRole.OWNER) {
            return transferOwnership(group, currentUser, userId);
        }
        if (newRole == GroupRole.LEADER || newRole == GroupRole.ADMIN) {
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
        
        List<Channel> channels = channelRepository.findAllByGroupId(groupId);
        if (!channels.isEmpty() && channels.get(0).getConversation() != null) {
            createAndBroadcastSystemMessage(
                    channels.get(0).getConversation(),
                    currentUser,
                    buildRoleChangeSystemContent(targetMembership.getUser().getUsername(), oldRole, newRole)
            );
        }

        List<GroupMember> members = groupMemberRepository.findAllByGroupId(groupId);
        return mapToGroupResponse(group, members);
    }

    private GroupResponse transferOwnership(Group group, User currentUser, String newOwnerId) {
        if (!group.getOwner().getId().equals(currentUser.getId())) {
            throw new UnauthorizedException("Only the current group owner can transfer ownership");
        }
        if (currentUser.getId().equals(newOwnerId)) {
            throw new BadRequestException("You are already the group owner");
        }

        GroupMember currentOwnerMembership = groupMemberRepository
                .findByGroupIdAndUserId(group.getId(), currentUser.getId())
                .orElseThrow(() -> new BadRequestException("Current owner membership was not found"));
        GroupMember newOwnerMembership = groupMemberRepository
                .findByGroupIdAndUserId(group.getId(), newOwnerId)
                .orElseThrow(() -> new BadRequestException("New owner must be a member of the group"));

        currentOwnerMembership.setRole(GroupRole.MEMBER);
        newOwnerMembership.setRole(GroupRole.OWNER);
        group.setOwner(newOwnerMembership.getUser());

        groupMemberRepository.save(currentOwnerMembership);
        groupMemberRepository.save(newOwnerMembership);
        groupRepository.save(group);

        List<Channel> channels = channelRepository.findAllByGroupId(group.getId());
        if (!channels.isEmpty() && channels.get(0).getConversation() != null) {
            createAndBroadcastSystemMessage(
                    channels.get(0).getConversation(),
                    currentUser,
                    "đã chuyển quyền Trưởng nhóm cho " + newOwnerMembership.getUser().getUsername() + "."
            );
        }

        return mapToGroupResponse(group, groupMemberRepository.findAllByGroupId(group.getId()));
    }

    public GroupResponse getGroupById(String groupId) {
        Group group = getGroupOrThrow(groupId);
        List<GroupMember> members = groupMemberRepository.findAllByGroupId(groupId);
        return mapToGroupResponse(group, members);
    }

    public List<GroupResponse> getMyGroups() {
        User currentUser = userService.getCurrentAuthenticatedUser();
        List<GroupMember> memberships = groupMemberRepository.findAllByUserId(currentUser.getId());
        Set<String> groupIds = memberships.stream()
                .map(m -> m.getGroup().getId())
                .collect(Collectors.toSet());
        List<Group> groups = groupRepository.findAllByOwnerIdOrIdIn(currentUser.getId(), groupIds);
        return groups.stream()
                .collect(Collectors.toMap(Group::getId, g -> g, (first, ignored) -> first, LinkedHashMap::new))
                .values()
                .stream()
                .map(g -> {
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

    private boolean isModeratorRole(GroupRole role) {
        return role == GroupRole.OWNER || role == GroupRole.LEADER || role == GroupRole.ADMIN || role == GroupRole.DEPUTY;
    }

    private String generateUniqueInviteCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 10);
        } while (groupRepository.findByInviteCode(code).isPresent());
        return code;
    }

    public GroupResponse refreshInviteCode(String groupId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = getGroupOrThrow(groupId);
        assertCanManageGroupSettings(group, currentUser);

        group.setInviteCode(generateUniqueInviteCode());
        Group saved = groupRepository.save(group);
        return mapToGroupResponse(saved, groupMemberRepository.findAllByGroupId(groupId));
    }

    public PublicGroupInfoResponse getPublicGroupInfoByInviteCode(String code) {
        Group group = groupRepository.findByInviteCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid invite code"));

        int memberCount = groupMemberRepository.countByGroupId(group.getId());
        
        // Fetch up to 4 members for the public group info preview avatar
        List<GroupMemberResponse> previewMembers = groupMemberRepository.findAllByGroupId(group.getId()).stream()
                .limit(4)
                .map(m -> GroupMemberResponse.builder()
                        .userId(m.getUser().getId())
                        .username(m.getUser().getUsername())
                        .avatarUrl(m.getUser().getAvatarUrl())
                        .role(m.getRole().name())
                        .build())
                .collect(Collectors.toList());

        return PublicGroupInfoResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .avatarUrl(group.getAvatarUrl())
                .ownerUsername(group.getOwner() != null ? group.getOwner().getUsername() : "Unknown")
                .memberCount(memberCount)
                .requiresApproval(group.isRequiresApproval())
                .members(previewMembers)
                .build();
    }

    public void joinGroupByInviteCode(String code) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = groupRepository.findByInviteCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid invite code"));

        if (groupMemberRepository.existsByGroupIdAndUserId(group.getId(), currentUser.getId())) {
            throw new BadRequestException("You are already a member of this group");
        }

        if (group.isRequiresApproval()) {
            Optional<GroupInvitation> existing = groupInvitationRepository.findByGroupIdAndInviteeId(group.getId(), currentUser.getId());
            if (existing.isPresent()) {
                throw new BadRequestException("You already have a pending request to join this group");
            }
            GroupInvitation invitation = GroupInvitation.builder()
                    .group(group)
                    .inviter(currentUser) // User requests to join (inviter = invitee)
                    .invitee(currentUser)
                    .status(InvitationStatus.WAITING_APPROVAL)
                    .build();
            groupInvitationRepository.save(invitation);
        } else {
            doAddMember(group, currentUser.getId(), currentUser);
        }
    }

    public void leaveGroup(String groupId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));

        // Owner cannot leave – must transfer ownership or delete the group
        if (group.getOwner() != null && group.getOwner().getId().equals(currentUser.getId())) {
            throw new BadRequestException("Trưởng nhóm không thể thoát nhóm. Hãy chuyển quyền trưởng nhóm hoặc xóa nhóm.");
        }

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, currentUser.getId())) {
            throw new BadRequestException("Bạn không phải thành viên của nhóm này.");
        }

        groupMemberRepository.deleteByGroupIdAndUserId(groupId, currentUser.getId());

        // Also remove from conversation members of all channels
        List<Channel> channels = channelRepository.findAllByGroupId(groupId);
        for (Channel ch : channels) {
            if (ch.getConversation() != null) {
                ch.getConversation().getMembers().removeIf(m -> m.getId().equals(currentUser.getId()));
                conversationRepository.save(ch.getConversation());
            }
        }

        // Broadcast system message to first public channel
        if (!channels.isEmpty() && channels.get(0).getConversation() != null) {
            createAndBroadcastSystemMessage(channels.get(0).getConversation(), currentUser, "đã rời khỏi nhóm.");
        }
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
        return "đã cập nhật quyền của " + username + " thành " + roleLabel(newRole) + ".";
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

        List<ChannelResponse> channelResponses = channelRepository.findAllByGroupId(group.getId()).stream()
                .map(ch -> ChannelResponse.builder()
                        .id(ch.getId())
                        .name(ch.getName())
                        .type(ch.getType())
                        .isPrivate(ch.isPrivate())
                        .groupId(ch.getGroup() != null ? ch.getGroup().getId() : null)
                        .conversationId(ch.getConversation() != null ? ch.getConversation().getId() : null)
                        .createdAt(ch.getCreatedAt())
                        .updatedAt(ch.getUpdatedAt())
                        .build()
                ).collect(Collectors.toList());

        int pendingApprovalCount = groupInvitationRepository.countByGroupIdAndStatus(group.getId(), InvitationStatus.WAITING_APPROVAL);

        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .avatarUrl(group.getAvatarUrl())
                .conversationId(channelResponses.stream()
                        .filter(ch -> "Chung".equals(ch.getName()))
                        .findFirst()
                        .or(() -> channelResponses.stream()
                                .filter(ch -> ch.getType() == ChannelType.TEXT && !ch.isPrivate())
                                .findFirst())
                        .or(() -> channelResponses.stream().findFirst())
                        .map(ChannelResponse::getConversationId)
                        .orElse(null))
                .ownerId(group.getOwner().getId())
                .ownerUsername(group.getOwner().getUsername())
                .channels(channelResponses)
                .members(memberResponses)
                .memberCount(memberResponses.size())
                .requiresApproval(group.isRequiresApproval())
                .inviteCode(group.getInviteCode())
                .pendingApprovalCount(pendingApprovalCount)
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
