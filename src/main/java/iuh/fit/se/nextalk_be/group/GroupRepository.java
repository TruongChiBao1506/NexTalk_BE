package iuh.fit.se.nextalk_be.group;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends MongoRepository<Group, String> {

    List<Group> findAllByOwnerIdOrIdIn(String ownerId, Collection<String> ids);

    boolean existsByOwnerId(String ownerId);

    Optional<Group> findByInviteCode(String inviteCode);

}
