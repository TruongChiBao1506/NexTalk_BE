package iuh.fit.se.nextalk_be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Path;
import java.util.TimeZone;

import jakarta.annotation.PostConstruct;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class NexTalkBeApplication {

    @PostConstruct
    public void init() {
        // Force the application to use Vietnam time (GMT+7) so it matches local development when deployed to Render
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

    public static void main(String[] args) {
        LocalEnvironmentFileLoader.load(
                Path.of("").toAbsolutePath(),
                System.getenv(),
                System.getProperties()
        );
        SpringApplication.run(NexTalkBeApplication.class, args);
    }
}
