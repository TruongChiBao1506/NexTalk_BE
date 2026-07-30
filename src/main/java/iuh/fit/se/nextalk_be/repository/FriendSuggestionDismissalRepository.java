package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.FriendSuggestionDismissal;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FriendSuggestionDismissalRepository
        extends MongoRepository<FriendSuggestionDismissal, String> {

    List<FriendSuggestionDismissal> findAllByUserIdAndExpiresAtAfter(
            String userId,
            LocalDateTime now);

    Optional<FriendSuggestionDismissal> findByUserIdAndCandidateUserId(
            String userId,
            String candidateUserId);
}
