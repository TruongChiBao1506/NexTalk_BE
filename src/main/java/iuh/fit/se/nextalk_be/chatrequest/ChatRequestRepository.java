package iuh.fit.se.nextalk_be.chatrequest;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRequestRepository extends MongoRepository<ChatRequest, String> {
    List<ChatRequest> findByReceiverIdAndStatusOrderByCreatedAtDesc(String receiverId, ChatRequestStatus status);
    List<ChatRequest> findBySenderIdAndStatusOrderByCreatedAtDesc(String senderId, ChatRequestStatus status);
    Optional<ChatRequest> findBySenderIdAndReceiverIdAndStatus(String senderId, String receiverId, ChatRequestStatus status);
    long countBySenderIdAndCreatedAtAfter(String senderId, LocalDateTime createdAt);
}
