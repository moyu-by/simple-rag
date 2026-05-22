package com.example.demo.exception;

/**
 * 限流异常。由 {@link com.example.demo.aspect.RateLimitAspect} 抛出，
 * 由 {@code GlobalExceptionHandler} 统一处理为 429 Too Many Requests。
 */
public class RateLimitException extends RuntimeException {

    public RateLimitException(String message) {
        super(message);
    }
}
