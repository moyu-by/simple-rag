package org.moyu.rag.exception;

/**
 * 限流异常。由 {@link org.moyu.rag.aspect.RateLimitAspect} 抛出，
 * 由 {@code GlobalExceptionHandler} 统一处理为 429 Too Many Requests。
 */
public class RateLimitException extends RuntimeException {

    public RateLimitException(String message) {
        super(message);
    }
}
