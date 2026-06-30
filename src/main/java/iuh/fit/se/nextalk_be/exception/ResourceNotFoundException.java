package iuh.fit.se.nextalk_be.exception;

import iuh.fit.se.nextalk_be.exception.AppException;


import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends AppException {
    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
