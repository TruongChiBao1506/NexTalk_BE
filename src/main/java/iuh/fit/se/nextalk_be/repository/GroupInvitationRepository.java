package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.GroupInvitation;
import iuh.fit.se.nextalk_be.entity.InvitationStatus;


import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupInvitationRepository extends MongoRepository<GroupInvitation, String> {
    List<GroupInvitation> findAllByInviteeIdAndStatus(String inviteeId, InvitationStatus status);
    List<GroupInvitation> findAllByGroupIdAndStatus(String groupId, InvitationStatus status);
    int countByGroupIdAndStatus(String groupId, InvitationStatus status);
    Optional<GroupInvitation> findByGroupIdAndInviteeId(String groupId, String inviteeId);
    boolean existsByGroupIdAndInviteeId(String groupId, String inviteeId);
    void deleteByGroupIdAndInviteeId(String groupId, String inviteeId);
    void deleteAllByGroupId(String groupId);
}
