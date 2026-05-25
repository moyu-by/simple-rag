package org.moyu.rag.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
public class JwtException extends RuntimeException {
    private final Type type;

    @Getter
    @AllArgsConstructor
    public enum Type {
        EXPIRED("token已过期"),
        INVALID("无效的token"),
        EMPTY("token为空"),
        CONFIG_ERROR("jwt配置错误");

        private final String message;
    }

    public JwtException(Type type) {
        super(type.getMessage());
        this.type = type;
    }

    public JwtException(Type type, Throwable cause) {
        super(type.getMessage(), cause);
        this.type = type;
    }

    public JwtException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }
}
