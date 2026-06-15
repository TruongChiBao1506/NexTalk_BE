package iuh.fit.se.nextalk_be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.io.File;
import java.nio.file.Files;
import java.util.TimeZone;
import jakarta.annotation.PostConstruct;

@SpringBootApplication
public class NexTalkBeApplication {

    @PostConstruct
    public void init() {
        // Force the application to use Vietnam time (GMT+7) so it matches local development when deployed to Render
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

    public static void main(String[] args) {
        loadEnv();
        SpringApplication.run(NexTalkBeApplication.class, args);
    }

    private static void loadEnv() {
        try {
            File currentDir = new File(".").getAbsoluteFile();
            File envFile = null;
            while (currentDir != null) {
                File possibleEnv = new File(currentDir, ".env");
                if (possibleEnv.exists() && possibleEnv.isFile()) {
                    envFile = possibleEnv;
                    break;
                }
                currentDir = currentDir.getParentFile();
            }

            if (envFile != null) {
                System.out.println("Loading environment variables from: " + envFile.getAbsolutePath());
                Files.lines(envFile.toPath())
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .forEach(line -> {
                            int eqIdx = line.indexOf('=');
                            if (eqIdx > 0) {
                                String key = line.substring(0, eqIdx).trim();
                                String val = line.substring(eqIdx + 1).trim();
                                if (val.startsWith("\"") && val.endsWith("\"")) {
                                    val = val.substring(1, val.length() - 1);
                                } else if (val.startsWith("'") && val.endsWith("'")) {
                                    val = val.substring(1, val.length() - 1);
                                }
                                System.setProperty(key, val);
                            }
                        });
            } else {
                System.err.println("Warning: .env file not found in current directory or any parent directories.");
            }
        } catch (Exception e) {
            System.err.println("Failed to load .env file: " + e.getMessage());
        }
    }
}
