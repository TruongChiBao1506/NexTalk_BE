package iuh.fit.se.nextalk_be.service;

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

public interface UserBlockService {
    public BlockStatusResponse block(String userId);
    public BlockStatusResponse unblock(String userId);
    public BlockStatusResponse getStatus(String userId);
}
