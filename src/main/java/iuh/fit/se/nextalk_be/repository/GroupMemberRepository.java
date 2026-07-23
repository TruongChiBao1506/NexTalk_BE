package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.GroupMember;


import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupMemberRepository extends MongoRepository<GroupMember, String> {

    List<GroupMember> findAllByGroupId(String groupId);

    @org.springframework.data.mongodb.repository.Query("{'$or': [{'group': {'$in': ?0}}, {'group.$id': {'$in': ?0}}, {'group._id': {'$in': ?0}}, {'group': {'$in': ?1}}, {'group.$id': {'$in': ?1}}, {'group._id': {'$in': ?1}}]}")
    List<GroupMember> findAllByGroupIdIn(java.util.Collection<String> stringIds, java.util.Collection<org.bson.types.ObjectId> objectIds);

    List<GroupMember> findAllByUserId(String userId);

    Optional<GroupMember> findByGroupIdAndUserId(String groupId, String userId);

    boolean existsByGroupIdAndUserId(String groupId, String userId);

    int countByGroupId(String groupId);

    void deleteByGroupIdAndUserId(String groupId, String userId);
}
