package iuh.fit.se.nextalk_be.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class StartupMigrationDisabledTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DataMigrationRunner.class, MessageDenormalizationMigration.class);

    @Test
    void migrationsAreAbsentFromNormalStartup() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(DataMigrationRunner.class);
            assertThat(context).doesNotHaveBean(MessageDenormalizationMigration.class);
        });
    }
}
