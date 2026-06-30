package iuh.fit.se.nextalk_be.service;

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

public interface ChannelService {
    public ChannelResponse createChannel(String groupId, CreateChannelRequest request);
    public ChannelResponse updateChannel(String groupId, String channelId, UpdateChannelRequest request);
    public void deleteChannel(String groupId, String channelId);
    public List<ChannelResponse> getChannelsByGroupId(String groupId);
    public ChannelResponse mapToResponse(Channel channel);
}
