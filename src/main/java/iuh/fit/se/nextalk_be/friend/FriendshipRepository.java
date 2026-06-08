package iuh.fit.se.nextalk_be.friend;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FriendshipRepository extends MongoRepository<Friendship, UUID> {

    @Query("{ '$or': [ { 'sender': ?0, 'receiver': ?1 }, { 'sender': ?1, 'receiver': ?0 } ] }")
    Optional<Friendship> findFriendshipBetweenUsers(UUID user1Id, UUID user2Id);

    @Query("{ '$or': [ { 'sender': ?0, 'receiver': ?1 }, { 'sender': ?1, 'receiver': ?0 } ] }")
    Optional<Friendship> findRelation(UUID userId, UUID friendId);

    List<Friendship> findByReceiverIdAndStatus(UUID receiverId, FriendshipStatus status);

    List<Friendship> findBySenderIdAndStatus(UUID senderId, FriendshipStatus status);

    @Query("{ '$or': [ { 'sender': ?0 }, { 'receiver': ?0 } ], 'status': ?1 }")
    List<Friendship> findAllByUserIdAndStatus(UUID userId, FriendshipStatus status);

    Optional<Friendship> findBySenderIdAndReceiverIdAndStatus(UUID senderId, UUID receiverId, FriendshipStatus status);
}
