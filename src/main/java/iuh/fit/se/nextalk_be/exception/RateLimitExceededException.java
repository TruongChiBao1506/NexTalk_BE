package iuh.fit.se.nextalk_be.exception;

import iuh.fit.se.nextalk_be.exception.AppException;


import org.springframework.http.HttpStatus;
import lombok.Getter;

@Getter
public class RateLimitExceededException extends AppException {
    private final long retryAfterSeconds;

    public RateLimitExceededException(String message) {
        this(message, 60);
    }

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
