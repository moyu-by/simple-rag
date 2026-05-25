package org.moyu.rag.exception;

import lombok.Getter;

@Getter
public class AuthException extends RuntimeException {

    public enum Type {
        PASSWORD_ERROR,
        TOKEN_EXPIRED,
        UNAUTHORIZED,
        FORBIDDEN
    }

    private final Type type;

    public AuthException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public AuthException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }
}
