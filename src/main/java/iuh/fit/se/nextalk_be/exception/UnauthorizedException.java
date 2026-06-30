package iuh.fit.se.nextalk_be.exception;

import iuh.fit.se.nextalk_be.exception.AppException;


import org.springframework.http.HttpStatus;

public class UnauthorizedException extends AppException {
    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
