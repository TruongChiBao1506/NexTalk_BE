package iuh.fit.se.nextalk_be;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LocalEnvironmentFileLoaderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsLocalValuesWithoutLoggingOrOverwritingProcessConfiguration() throws Exception {
        Files.writeString(temporaryDirectory.resolve(".env"), """
                LOCAL_ONLY='local-value'
                EXISTING=unsafe-file-value
                # ignored
                """);
        Map<String, String> environment = new HashMap<>();
        environment.put("EXISTING", "deployment-value");
        Properties properties = new Properties();

        LocalEnvironmentFileLoader.load(temporaryDirectory, environment, properties);

        assertEquals("local-value", properties.getProperty("LOCAL_ONLY"));
        assertNull(properties.getProperty("EXISTING"));
    }

    @Test
    void neverLoadsDotEnvInProduction() throws Exception {
        Files.writeString(temporaryDirectory.resolve(".env"), "SHOULD_NOT_LOAD=private-value");
        Map<String, String> environment = Map.of("SPRING_PROFILES_ACTIVE", "prod");
        Properties properties = new Properties();

        LocalEnvironmentFileLoader.load(temporaryDirectory, environment, properties);

        assertNull(properties.getProperty("SHOULD_NOT_LOAD"));
    }

    @Test
    void existingSystemPropertyWinsOverDotEnv() throws Exception {
        Files.writeString(temporaryDirectory.resolve(".env"), "EXISTING=unsafe-file-value");
        Properties properties = new Properties();
        properties.setProperty("EXISTING", "explicit-value");

        LocalEnvironmentFileLoader.load(temporaryDirectory, Map.of(), properties);

        assertEquals("explicit-value", properties.getProperty("EXISTING"));
    }
}
