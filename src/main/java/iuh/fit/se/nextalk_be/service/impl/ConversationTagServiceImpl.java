package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.request.AssignConversationTagRequest;
import iuh.fit.se.nextalk_be.dto.request.CreateConversationTagRequest;
import iuh.fit.se.nextalk_be.dto.response.ConversationTagDataResponse;
import iuh.fit.se.nextalk_be.dto.response.ConversationTagResponse;
import iuh.fit.se.nextalk_be.entity.ConversationTag;
import iuh.fit.se.nextalk_be.entity.ConversationTagMapping;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.ConversationTagMappingRepository;
import iuh.fit.se.nextalk_be.repository.ConversationTagRepository;
import iuh.fit.se.nextalk_be.service.ConversationTagService;
import iuh.fit.se.nextalk_be.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationTagServiceImpl implements ConversationTagService {

    private static final List<Map<String, String>> DEFAULT_TAGS = List.of(
            Map.of("name", "Khách hàng", "color", "#EF4444"),
            Map.of("name", "Gia đình", "color", "#EC4899"),
            Map.of("name", "Công việc", "color", "#F97316"),
            Map.of("name", "Bạn bè", "color", "#EAB308"),
            Map.of("name", "Trả lời sau", "color", "#22C55E"),
            Map.of("name", "Đồng nghiệp", "color", "#3B82F6")
    );

    private final ConversationTagRepository tagRepository;
    private final ConversationTagMappingRepository mappingRepository;
    private final UserService userService;

    @Override
    public ConversationTagDataResponse getUserTagData() {
        User currentUser = userService.getCurrentAuthenticatedUser();
        List<ConversationTag> tags = tagRepository.findByUserOrderByPositionAscCreatedAtAsc(currentUser);
        if (tags.isEmpty()) {
            tags = seedDefaultTags(currentUser);
        }

        List<ConversationTagMapping> mappings = mappingRepository.findByUser(currentUser);
        Map<String, List<String>> mappingMap = new HashMap<>();
        for (ConversationTagMapping mapping : mappings) {
            if (mapping.getTag() != null && mapping.getTargetId() != null) {
                mappingMap.computeIfAbsent(mapping.getTargetId(), k -> new ArrayList<>())
                        .add(mapping.getTag().getId());
            }
        }

        return ConversationTagDataResponse.builder()
                .tags(tags.stream().map(this::mapToResponse).collect(Collectors.toList()))
                .mappings(mappingMap)
                .build();
    }

    @Override
    public ConversationTagResponse createTag(CreateConversationTagRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        String name = request.getName().trim();
        if (tagRepository.existsByUserAndNameIgnoreCase(currentUser, name)) {
            throw new BadRequestException("Thẻ phân loại với tên này đã tồn tại");
        }

        List<ConversationTag> existingTags = tagRepository.findByUserOrderByPositionAscCreatedAtAsc(currentUser);
        int nextPosition = existingTags.size();

        ConversationTag tag = ConversationTag.builder()
                .user(currentUser)
                .name(name)
                .color(request.getColor().trim())
                .position(nextPosition)
                .build();

        ConversationTag saved = tagRepository.save(tag);
        return mapToResponse(saved);
    }

    @Override
    public ConversationTagResponse updateTag(String tagId, CreateConversationTagRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        ConversationTag tag = tagRepository.findByIdAndUser(tagId, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Thẻ phân loại không tồn tại"));

        String newName = request.getName().trim();
        if (!tag.getName().equalsIgnoreCase(newName) && tagRepository.existsByUserAndNameIgnoreCase(currentUser, newName)) {
            throw new BadRequestException("Thẻ phân loại với tên này đã tồn tại");
        }

        tag.setName(newName);
        tag.setColor(request.getColor().trim());
        ConversationTag updated = tagRepository.save(tag);
        return mapToResponse(updated);
    }

    @Override
    public void deleteTag(String tagId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        ConversationTag tag = tagRepository.findByIdAndUser(tagId, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Thẻ phân loại không tồn tại"));

        mappingRepository.deleteByTag(tag);
        tagRepository.delete(tag);
    }

    @Override
    public ConversationTagDataResponse assignTag(String tagId, AssignConversationTagRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        ConversationTag tag = tagRepository.findByIdAndUser(tagId, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Thẻ phân loại không tồn tại"));

        if (request == null || request.getTargetId() == null || request.getTargetId().isBlank()) {
            throw new BadRequestException("Target ID không được để trống");
        }

        String targetId = request.getTargetId().trim();
        Optional<ConversationTagMapping> existing = mappingRepository.findByUserAndTagAndTargetId(currentUser, tag, targetId);
        if (existing.isEmpty()) {
            ConversationTagMapping mapping = ConversationTagMapping.builder()
                    .user(currentUser)
                    .tag(tag)
                    .targetType(request.getTargetType() != null ? request.getTargetType() : "DM")
                    .targetId(targetId)
                    .build();
            mappingRepository.save(mapping);
        }

        return getUserTagData();
    }

    @Override
    public ConversationTagDataResponse unassignTag(String tagId, String targetId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        ConversationTag tag = tagRepository.findByIdAndUser(tagId, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Thẻ phân loại không tồn tại"));

        if (targetId == null || targetId.isBlank()) {
            throw new BadRequestException("Target ID không được để trống");
        }

        mappingRepository.deleteByUserAndTagAndTargetId(currentUser, tag, targetId.trim());
        return getUserTagData();
    }

    private List<ConversationTag> seedDefaultTags(User user) {
        List<ConversationTag> seeded = new ArrayList<>();
        int pos = 0;
        for (Map<String, String> def : DEFAULT_TAGS) {
            ConversationTag tag = ConversationTag.builder()
                    .user(user)
                    .name(def.get("name"))
                    .color(def.get("color"))
                    .position(pos++)
                    .build();
            seeded.add(tagRepository.save(tag));
        }
        return seeded;
    }

    private ConversationTagResponse mapToResponse(ConversationTag tag) {
        return ConversationTagResponse.builder()
                .id(tag.getId())
                .name(tag.getName())
                .color(tag.getColor())
                .position(tag.getPosition())
                .createdAt(tag.getCreatedAt())
                .build();
    }
}
