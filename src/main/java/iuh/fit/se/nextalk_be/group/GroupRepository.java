package iuh.fit.se.nextalk_be.group;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface GroupRepository extends MongoRepository<Group, UUID> {

    List<Group> findAllByOwnerIdOrIdIn(UUID ownerId, Collection<UUID> ids);
}
