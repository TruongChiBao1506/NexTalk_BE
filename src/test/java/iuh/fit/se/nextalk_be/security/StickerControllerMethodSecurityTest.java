package iuh.fit.se.nextalk_be.security;

import iuh.fit.se.nextalk_be.controller.StickerController;
import iuh.fit.se.nextalk_be.dto.request.AddStickersRequest;
import iuh.fit.se.nextalk_be.dto.request.StickerPackRequest;
import iuh.fit.se.nextalk_be.dto.request.ToggleRequest;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.entity.UserRole;
import iuh.fit.se.nextalk_be.service.StickerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(StickerControllerMethodSecurityTest.Config.class)
class StickerControllerMethodSecurityTest {

    @org.springframework.beans.factory.annotation.Autowired
    private StickerController controller;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void regularUserCannotCallAnyStickerMutation() {
        authenticate(UserRole.USER);

        assertThrows(AccessDeniedException.class, () -> controller.createPack(new StickerPackRequest()));
        assertThrows(AccessDeniedException.class,
                () -> controller.addStickersToPack("pack-1", new AddStickersRequest()));
        assertThrows(AccessDeniedException.class,
                () -> controller.togglePackActive("pack-1", new ToggleRequest()));
        assertThrows(AccessDeniedException.class,
                () -> controller.toggleStickerActive("pack-1", "sticker-1", new ToggleRequest()));
        assertThrows(AccessDeniedException.class,
                () -> controller.deleteSticker("pack-1", "sticker-1"));
    }

    @Test
    void adminCanCallStickerMutation() {
        authenticate(UserRole.ADMIN);
        assertDoesNotThrow(() -> controller.deleteSticker("pack-1", "sticker-1"));
    }

    private void authenticate(UserRole role) {
        User user = User.builder()
                .email(role.name().toLowerCase() + "@example.test")
                .username(role.name().toLowerCase())
                .role(role)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        StickerService stickerService() {
            return mock(StickerService.class);
        }

        @Bean
        StickerController stickerController(StickerService stickerService) {
            return new StickerController(stickerService);
        }
    }
}
