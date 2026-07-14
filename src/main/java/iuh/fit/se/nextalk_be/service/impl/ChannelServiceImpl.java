package iuh.fit.se.nextalk_be.service.impl;
import iuh.fit.se.nextalk_be.service.ChannelService;

import iuh.fit.se.nextalk_be.dto.request.CreateChannelRequest;
import iuh.fit.se.nextalk_be.dto.request.UpdateChannelRequest;
import iuh.fit.se.nextalk_be.dto.response.ChannelResponse;
import iuh.fit.se.nextalk_be.entity.Channel;
import iuh.fit.se.nextalk_be.entity.ChannelType;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationType;
import iuh.fit.se.nextalk_be.entity.Group;
import iuh.fit.se.nextalk_be.entity.GroupMember;
import iuh.fit.se.nextalk_be.entity.GroupRole;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.exception.UnauthorizedException;
import iuh.fit.se.nextalk_be.repository.ChannelRepository;
import iuh.fit.se.nextalk_be.repository.ChannelTaskRepository;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.GroupMemberRepository;
import iuh.fit.se.nextalk_be.repository.GroupRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.UserService;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChannelServiceImpl implements ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelTaskRepository channelTaskRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ConversationRepository conversationRepository;
    private final UserService userService;
    private final UserRepository userRepository;

    public ChannelResponse createChannel(String groupId, CreateChannelRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

        if (!isLeaderRole(getRole(group, currentUser).orElse(null))) {
            throw new UnauthorizedException("Only group leaders can create channels");
        }

        Conversation conversation = Conversation.builder()
                .type(ConversationType.GROUP)
                .name(request.getName())
                .owner(currentUser)
                .members(new HashSet<>())
                .build();

        if (!request.isPrivate()) {
            List<GroupMember> members = groupMemberRepository.findAllByGroupId(groupId);
            Set<User> conversationMembers = members.stream().map(GroupMember::getUser).collect(Collectors.toSet());
            conversationMembers.add(group.getOwner());
            conversation.setMembers(conversationMembers);
        } else {
            Set<User> privateMembers = new HashSet<>();
            privateMembers.add(currentUser);
            
            if (request.getMemberIds() != null && !request.getMemberIds().isEmpty()) {
                List<GroupMember> groupMembers = groupMemberRepository.findAllByGroupId(groupId);
                Set<String> validUserIds = groupMembers.stream()
                        .map(gm -> gm.getUser().getId())
                        .collect(Collectors.toSet());
                validUserIds.add(group.getOwner().getId());
                
                for (String memberId : request.getMemberIds()) {
                    if (validUserIds.contains(memberId)) {
                        userRepository.findById(memberId).ifPresent(privateMembers::add);
                    }
                }
            }
            
            conversation.setMembers(privateMembers);
        }

        Conversation savedConversation = conversationRepository.save(conversation);

        Channel channel = Channel.builder()
                .name(request.getName())
                .type(request.getType() != null ? request.getType() : ChannelType.TEXT)
                .isPrivate(request.isPrivate())
                .group(group)
                .conversation(savedConversation)
                .build();

        Channel savedChannel = channelRepository.save(channel);
        return mapToResponse(savedChannel);
    }

    public ChannelResponse updateChannel(String groupId, String channelId, UpdateChannelRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

        if (!isLeaderRole(getRole(group, currentUser).orElse(null))) {
            throw new UnauthorizedException("Only group leaders can update channels");
        }

        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResourceNotFoundException("Channel not found"));

        if (!channel.getGroup().getId().equals(groupId)) {
            throw new BadRequestException("Channel does not belong to this group");
        }

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            channel.setName(request.getName().trim());
            if (channel.getConversation() != null) {
                channel.getConversation().setName(request.getName().trim());
                conversationRepository.save(channel.getConversation());
            }
        }
        if (request.getType() != null) {
            channel.setType(request.getType());
        }
        if (request.getIsPrivate() != null) {
            channel.setPrivate(request.getIsPrivate());
            
            Conversation conversation = channel.getConversation();
            if (conversation != null) {
                if (request.getIsPrivate()) {
                    Set<User> privateMembers = new HashSet<>();
                    privateMembers.add(currentUser);
                    
                    if (request.getMemberIds() != null) {
                        List<GroupMember> groupMembers = groupMemberRepository.findAllByGroupId(groupId);
                        Set<String> validUserIds = groupMembers.stream()
                                .map(gm -> gm.getUser().getId())
                                .collect(Collectors.toSet());
                        validUserIds.add(group.getOwner().getId());
                        
                        for (String memberId : request.getMemberIds()) {
                            if (validUserIds.contains(memberId)) {
                                userRepository.findById(memberId).ifPresent(privateMembers::add);
                            }
                        }
                    } else {
                        // Keep existing members if memberIds is not provided but channel is already private
                        privateMembers.addAll(conversation.getMembers());
                    }
                    conversation.setMembers(privateMembers);
                } else {
                    List<GroupMember> members = groupMemberRepository.findAllByGroupId(groupId);
                    Set<User> conversationMembers = members.stream().map(GroupMember::getUser).collect(Collectors.toSet());
                    conversationMembers.add(group.getOwner());
                    conversation.setMembers(conversationMembers);
                }
                conversationRepository.save(conversation);
            }
        } else if (channel.isPrivate() && request.getMemberIds() != null) {
            Conversation conversation = channel.getConversation();
            if (conversation != null) {
                Set<User> privateMembers = new HashSet<>();
                privateMembers.add(currentUser);
                
                List<GroupMember> groupMembers = groupMemberRepository.findAllByGroupId(groupId);
                Set<String> validUserIds = groupMembers.stream()
                        .map(gm -> gm.getUser().getId())
                        .collect(Collectors.toSet());
                validUserIds.add(group.getOwner().getId());
                
                for (String memberId : request.getMemberIds()) {
                    if (validUserIds.contains(memberId)) {
                        userRepository.findById(memberId).ifPresent(privateMembers::add);
                    }
                }
                conversation.setMembers(privateMembers);
                conversationRepository.save(conversation);
            }
        }

        Channel savedChannel = channelRepository.save(channel);
        return mapToResponse(savedChannel);
    }

    public void deleteChannel(String groupId, String channelId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

        if (!isLeaderRole(getRole(group, currentUser).orElse(null))) {
            throw new UnauthorizedException("Only group leaders can delete channels");
        }

        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResourceNotFoundException("Channel not found"));

        if (!channel.getGroup().getId().equals(groupId)) {
            throw new BadRequestException("Channel does not belong to this group");
        }

        if (channel.getConversation() != null) {
            conversationRepository.delete(channel.getConversation());
        }
        channelTaskRepository.deleteAllByChannelId(channelId);
        channelRepository.delete(channel);
    }

    public List<ChannelResponse> getChannelsByGroupId(String groupId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

        boolean isOwner = group.getOwner().getId().equals(currentUser.getId());
        boolean isMember = groupMemberRepository.existsByGroupIdAndUserId(groupId, currentUser.getId());

        if (!isOwner && !isMember) {
            throw new UnauthorizedException("You are not a member of this group");
        }

        List<Channel> channels = channelRepository.findAllByGroupId(groupId);
        return channels.stream()
                .filter(ch -> !ch.isPrivate() || isUserInConversation(ch.getConversation(), currentUser.getId()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private boolean isUserInConversation(Conversation conversation, String userId) {
        if (conversation == null || conversation.getMembers() == null) return false;
        return conversation.getMembers().stream().anyMatch(m -> m.getId().equals(userId));
    }

    private Optional<GroupRole> getRole(Group group, User user) {
        if (group.getOwner().getId().equals(user.getId())) {
            return Optional.of(GroupRole.OWNER);
        }
        return groupMemberRepository.findByGroupIdAndUserId(group.getId(), user.getId())
                .map(GroupMember::getRole);
    }

    private boolean isLeaderRole(GroupRole role) {
        return role != null && (role == GroupRole.OWNER || role == GroupRole.LEADER || role == GroupRole.ADMIN);
    }

    public ChannelResponse mapToResponse(Channel channel) {
        return ChannelResponse.builder()
                .id(channel.getId())
                .name(channel.getName())
                .type(channel.getType())
                .isPrivate(channel.isPrivate())
                .groupId(channel.getGroup() != null ? channel.getGroup().getId() : null)
                .conversationId(channel.getConversation() != null ? channel.getConversation().getId() : null)
                .createdAt(channel.getCreatedAt())
                .updatedAt(channel.getUpdatedAt())
                .build();
    }
}
