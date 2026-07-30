package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountSecurityService {
    private final UserRepository userRepository;
    private final SessionRevocationService sessionRevocationService;

    public void lockAndRevoke(User user) {
        if (!user.isAccountLocked()) {
            user.setAccountLocked(true);
            userRepository.save(user);
        }
        sessionRevocationService.revokeAllForUser(user);
    }
}
