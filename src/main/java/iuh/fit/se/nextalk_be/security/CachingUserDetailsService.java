package iuh.fit.se.nextalk_be.security;

import iuh.fit.se.nextalk_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service("cachingUserDetailsService")
@RequiredArgsConstructor
public class CachingUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Cacheable(value = "userDetailsByEmail", key = "#email")
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    @CacheEvict(value = "userDetailsByEmail", key = "#email")
    public void evict(String email) {
        // Evicts user details from Caffeine cache upon session revocation or security events
    }
}
