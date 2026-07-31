package iuh.fit.se.nextalk_be.conversation;

import iuh.fit.se.nextalk_be.dto.request.CreateConversationTagRequest;
import iuh.fit.se.nextalk_be.entity.ConversationTag;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.ConversationTagMappingRepository;
import iuh.fit.se.nextalk_be.repository.ConversationTagRepository;
import iuh.fit.se.nextalk_be.service.UserService;
import iuh.fit.se.nextalk_be.service.impl.ConversationTagServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationTagSecurityTest {

    @Mock
    private ConversationTagRepository tagRepository;

    @Mock
    private ConversationTagMappingRepository mappingRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ConversationTagServiceImpl tagService;

    private User userA;
    private User userB;
    private ConversationTag tagOfUserB;

    @BeforeEach
    void setUp() {
        userA = User.builder().username("userA").build();
        userA.setId("user-a-id");

        userB = User.builder().username("userB").build();
        userB.setId("user-b-id");

        tagOfUserB = ConversationTag.builder()
                .user(userB)
                .name("Gia đình")
                .color("#EC4899")
                .position(0)
                .build();
        tagOfUserB.setId("tag-user-b");
    }

    @Test
    void userCannotUpdateTagBelongingToAnotherUser() {
        when(userService.getCurrentAuthenticatedUser()).thenReturn(userA);
        when(tagRepository.findByIdAndUser("tag-user-b", userA)).thenReturn(Optional.empty());

        CreateConversationTagRequest updateReq = CreateConversationTagRequest.builder()
                .name("Hack Name")
                .color("#000000")
                .build();

        assertThrows(ResourceNotFoundException.class, () -> tagService.updateTag("tag-user-b", updateReq));
        verify(tagRepository, never()).save(any());
    }

    @Test
    void userCannotDeleteTagBelongingToAnotherUser() {
        when(userService.getCurrentAuthenticatedUser()).thenReturn(userA);
        when(tagRepository.findByIdAndUser("tag-user-b", userA)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tagService.deleteTag("tag-user-b"));
        verify(tagRepository, never()).delete(any());
        verify(mappingRepository, never()).deleteByTag(any());
    }
}
