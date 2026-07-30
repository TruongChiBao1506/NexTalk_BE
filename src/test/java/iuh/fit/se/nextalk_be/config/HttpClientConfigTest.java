package iuh.fit.se.nextalk_be.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class HttpClientConfigTest {

    @Test
    void usesJdkClientWithFiniteTimeouts() {
        RestTemplate restTemplate = new HttpClientConfig().restTemplate(
                Duration.ofSeconds(2),
                Duration.ofSeconds(5)
        );

        assertInstanceOf(JdkClientHttpRequestFactory.class, restTemplate.getRequestFactory());
    }
}
