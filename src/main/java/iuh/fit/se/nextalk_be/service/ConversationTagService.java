package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.request.AssignConversationTagRequest;
import iuh.fit.se.nextalk_be.dto.request.CreateConversationTagRequest;
import iuh.fit.se.nextalk_be.dto.response.ConversationTagDataResponse;
import iuh.fit.se.nextalk_be.dto.response.ConversationTagResponse;

public interface ConversationTagService {
    ConversationTagDataResponse getUserTagData();
    ConversationTagResponse createTag(CreateConversationTagRequest request);
    ConversationTagResponse updateTag(String tagId, CreateConversationTagRequest request);
    void deleteTag(String tagId);
    ConversationTagDataResponse assignTag(String tagId, AssignConversationTagRequest request);
    ConversationTagDataResponse unassignTag(String tagId, String targetId);
}
