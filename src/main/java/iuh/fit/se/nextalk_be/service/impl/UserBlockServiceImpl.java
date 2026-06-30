package iuh.fit.se.nextalk_be.service.impl;
import iuh.fit.se.nextalk_be.service.UserBlockService;

import iuh.fit.se.nextalk_be.dto.response.BlockStatusResponse;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.entity.UserBlock;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.UserBlockRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.UserService;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserBlockServiceImpl implements UserBlockService {

    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    public BlockStatusResponse block(String userId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        if (currentUser.getId().equals(userId)) {
            throw new BadRequestException("Cannot block yourself");
        }

        User blockedUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (!userBlockRepository.existsByBlockerIdAndBlockedId(currentUser.getId(), userId)) {
            userBlockRepository.save(UserBlock.builder()
                    .blocker(currentUser)
                    .blocked(blockedUser)
                    .build());
        }

        return getStatus(userId);
    }

    public BlockStatusResponse unblock(String userId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        userBlockRepository.findByBlockerIdAndBlockedId(currentUser.getId(), userId)
                .ifPresent(userBlockRepository::delete);
        return getStatus(userId);
    }

    public BlockStatusResponse getStatus(String userId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        if (currentUser.getId().equals(userId)) {
            return BlockStatusResponse.builder()
                    .userId(userId)
                    .blockedByMe(false)
                    .blockedMe(false)
                    .blocked(false)
                    .build();
        }

        boolean blockedByMe = userBlockRepository.existsByBlockerIdAndBlockedId(currentUser.getId(), userId);
        boolean blockedMe = userBlockRepository.existsByBlockerIdAndBlockedId(userId, currentUser.getId());
        return BlockStatusResponse.builder()
                .userId(userId)
                .blockedByMe(blockedByMe)
                .blockedMe(blockedMe)
                .blocked(blockedByMe || blockedMe)
                .build();
    }
}
