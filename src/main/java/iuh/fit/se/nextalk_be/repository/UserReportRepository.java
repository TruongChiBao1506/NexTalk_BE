package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.UserReport;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserReportRepository extends MongoRepository<UserReport, String> {
    List<UserReport> findByReportedUserIdOrderByCreatedAtDesc(String reportedUserId);
}
