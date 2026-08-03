package iuh.fit.se.nextalk_be.config;

import org.bson.Document;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration(proxyBeanMethods = false)
public class MongoApplicationHealthConfig {

    private static final String PING_COMMAND = "{ ping: 1 }";

    /**
     * Spring Boot 4's default indicator enumerates every visible database and
     * executes {@code hello} on each one. Atlas can expose the system database
     * names while correctly denying application users commands on {@code local}.
     * Checking through MongoTemplate keeps readiness scoped to the database the
     * application actually reads and writes.
     */
    @Bean(name = "mongoHealthIndicator")
    public HealthIndicator mongoHealthIndicator(MongoTemplate mongoTemplate) {
        return () -> {
            try {
                Document result = mongoTemplate.executeCommand(PING_COMMAND);
                Object ok = result.get("ok");
                if (ok instanceof Number number && number.doubleValue() == 1.0D) {
                    return Health.up().build();
                }
                return Health.down().build();
            } catch (RuntimeException exception) {
                // Do not expose connection strings, hosts, or credentials in health details.
                return Health.down().build();
            }
        };
    }
}
