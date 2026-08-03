package iuh.fit.se.nextalk_be.service;

import lombok.Getter;

@Getter
public class PushDeliveryException extends RuntimeException {
    private final String errorCode;
    private final boolean retryable;

    private PushDeliveryException(String errorCode, boolean retryable, Throwable cause) {
        super(errorCode, cause, false, false);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public static PushDeliveryException retryable(String errorCode, Throwable cause) {
        return new PushDeliveryException(errorCode, true, cause);
    }

    public static PushDeliveryException permanent(String errorCode, Throwable cause) {
        return new PushDeliveryException(errorCode, false, cause);
    }
}
