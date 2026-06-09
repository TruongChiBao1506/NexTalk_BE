package iuh.fit.se.nextalk_be.friend;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendshipRepository extends MongoRepository<Friendship, String> {

    Optional<Friendship> findBySenderIdAndReceiverIdOrSenderIdAndReceiverId(String senderId1, String receiverId1, String senderId2, String receiverId2);

    default Optional<Friendship> findFriendshipBetweenUsers(String user1Id, String user2Id) {
        return findBySenderIdAndReceiverIdOrSenderIdAndReceiverId(user1Id, user2Id, user2Id, user1Id);
    }

    default Optional<Friendship> findRelation(String userId, String friendId) {
        return findBySenderIdAndReceiverIdOrSenderIdAndReceiverId(userId, friendId, friendId, userId);
    }

    List<Friendship> findByReceiverIdAndStatus(String receiverId, FriendshipStatus status);

    List<Friendship> findBySenderIdAndStatus(String senderId, FriendshipStatus status);

    List<Friendship> findBySenderIdAndStatusOrReceiverIdAndStatus(String senderId, FriendshipStatus status1, String receiverId, FriendshipStatus status2);

    default List<Friendship> findAllByUserIdAndStatus(String userId, FriendshipStatus status) {
        return findBySenderIdAndStatusOrReceiverIdAndStatus(userId, status, userId, status);
    }

    Optional<Friendship> findBySenderIdAndReceiverIdAndStatus(String senderId, String receiverId, FriendshipStatus status);
}
