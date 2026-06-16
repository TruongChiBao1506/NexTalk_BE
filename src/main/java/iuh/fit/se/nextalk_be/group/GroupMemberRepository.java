package iuh.fit.se.nextalk_be.group;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupMemberRepository extends MongoRepository<GroupMember, String> {

    List<GroupMember> findAllByGroupId(String groupId);

    List<GroupMember> findAllByUserId(String userId);

    Optional<GroupMember> findByGroupIdAndUserId(String groupId, String userId);

    boolean existsByGroupIdAndUserId(String groupId, String userId);

    int countByGroupId(String groupId);

    void deleteByGroupIdAndUserId(String groupId, String userId);
}
