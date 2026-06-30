package iuh.fit.se.nextalk_be.exception;

import iuh.fit.se.nextalk_be.exception.AppException;


import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends AppException {
    public RateLimitExceededException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
