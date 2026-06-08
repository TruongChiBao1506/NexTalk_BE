package iuh.fit.se.nextalk_be.group;

import iuh.fit.se.nextalk_be.conversation.Conversation;
import iuh.fit.se.nextalk_be.conversation.ConversationRepository;
import iuh.fit.se.nextalk_be.conversation.ConversationType;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.exception.UnauthorizedException;
import iuh.fit.se.nextalk_be.group.dto.*;
import iuh.fit.se.nextalk_be.notification.NotificationService;
import iuh.fit.se.nextalk_be.notification.NotificationType;
import iuh.fit.se.nextalk_be.user.User;
import iuh.fit.se.nextalk_be.user.UserRepository;
import iuh.fit.se.nextalk_be.user.UserService;
import lombok.RequiredArgsConstructor;
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

    @Transactional
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
            for (UUID memberId : request.getMemberIds()) {
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
            for (UUID memberId : request.getMemberIds()) {
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

    @Transactional
    public GroupResponse updateGroup(UUID groupId, UpdateGroupRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = getGroupOrThrow(groupId);

        // Only owner or admin can update
        assertOwnerOrAdmin(group, currentUser);

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            group.setName(request.getName().trim());
            if (group.getConversation() != null) {
                group.getConversation().setName(request.getName().trim());
                conversationRepository.save(group.getConversation());
            }
        }

        Group saved = groupRepository.save(group);
        List<GroupMember> members = groupMemberRepository.findAllByGroupId(groupId);
        return mapToGroupResponse(saved, members);
    }

    @Transactional
    public void deleteGroup(UUID groupId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = getGroupOrThrow(groupId);

        if (!group.getOwner().getId().equals(currentUser.getId())) {
            throw new UnauthorizedException("Only the group owner can delete this group");
        }

        groupMemberRepository.deleteAll(groupMemberRepository.findAllByGroupId(groupId));
        if (group.getConversation() != null) {
            conversationRepository.delete(group.getConversation());
        }
        groupRepository.delete(group);
    }

    @Transactional
    public GroupResponse addMember(UUID groupId, AddMemberRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = getGroupOrThrow(groupId);

        assertOwnerOrAdmin(group, currentUser);

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

    @Transactional
    public void removeMember(UUID groupId, UUID userId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = getGroupOrThrow(groupId);

        // Owner cannot be removed; only owner/admin can remove others; member can remove themselves
        if (group.getOwner().getId().equals(userId)) {
            throw new BadRequestException("Cannot remove the group owner");
        }

        boolean isSelf = currentUser.getId().equals(userId);
        if (!isSelf) {
            assertOwnerOrAdmin(group, currentUser);
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

    @Transactional(readOnly = true)
    public GroupResponse getGroupById(UUID groupId) {
        Group group = getGroupOrThrow(groupId);
        List<GroupMember> members = groupMemberRepository.findAllByGroupId(groupId);
        return mapToGroupResponse(group, members);
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> getMyGroups() {
        User currentUser = userService.getCurrentAuthenticatedUser();
        List<GroupMember> memberships = groupMemberRepository.findAllByUserId(currentUser.getId());
        Set<UUID> groupIds = memberships.stream()
                .map(m -> m.getGroup().getId())
                .collect(Collectors.toSet());
        List<Group> groups = groupRepository.findAllByOwnerIdOrIdIn(currentUser.getId(), groupIds);
        return groups.stream().map(g -> {
            List<GroupMember> members = groupMemberRepository.findAllByGroupId(g.getId());
            return mapToGroupResponse(g, members);
        }).collect(Collectors.toList());
    }

    // --- Private helpers ---

    private Group getGroupOrThrow(UUID groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));
    }

    private void assertOwnerOrAdmin(Group group, User user) {
        boolean isOwner = group.getOwner().getId().equals(user.getId());
        if (!isOwner) {
            Optional<GroupMember> membership = groupMemberRepository.findByGroupIdAndUserId(group.getId(), user.getId());
            boolean isAdmin = membership.map(m -> m.getRole() == GroupRole.ADMIN || m.getRole() == GroupRole.OWNER).orElse(false);
            if (!isAdmin) {
                throw new UnauthorizedException("Only the group owner or admin can perform this action");
            }
        }
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
                .ownerId(group.getOwner().getId())
                .ownerUsername(group.getOwner().getUsername())
                .conversationId(group.getConversation() != null ? group.getConversation().getId() : null)
                .members(memberResponses)
                .memberCount(memberResponses.size())
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .build();
    }
}
