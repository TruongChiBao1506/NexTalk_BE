package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.User;


import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    java.util.List<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(String username, String email);

    java.util.List<User> findByBirthdayNotNullAndEnableBirthdayNotificationTrue();
}
