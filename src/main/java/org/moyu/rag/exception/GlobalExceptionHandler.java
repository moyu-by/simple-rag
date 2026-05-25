package org.moyu.rag.exception;

import org.moyu.rag.common.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<Result<Void>> handleValidation(Exception e) {
        log.warn("参数校验失败", e);
        String msg = switch (e) {
            case MethodArgumentNotValidException me -> me.getBindingResult().getFieldErrors().stream()
                    .map(err -> err.getDefaultMessage())
                    .collect(Collectors.joining("，"));
            case MissingServletRequestParameterException me -> "缺少参数: " + me.getParameterName();
            default -> e.getMessage();
        };
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(HttpStatus.BAD_REQUEST.value(), msg));
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<Result<Void>> handleJwt(JwtException e) {
        log.error("JWT 异常", e);
        ResponseEntity<Result<Void>> result = switch (e.getType()) {
            case EMPTY, INVALID, EXPIRED ->
                    ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Result.fail(HttpStatus.UNAUTHORIZED.value(), e.getMessage()));
            case CONFIG_ERROR ->
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Result.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage()));
        };
        return result;
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Result<Void>> handleAuth(AuthException e) {
        log.warn("认证异常", e);
        ResponseEntity<Result<Void>> result = switch (e.getType()) {
            case PASSWORD_ERROR, FORBIDDEN ->
                    ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Result.fail(HttpStatus.BAD_REQUEST.value(), e.getMessage()));
            case TOKEN_EXPIRED, UNAUTHORIZED ->
                    ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Result.fail(HttpStatus.UNAUTHORIZED.value(), e.getMessage()));
        };
        return result;
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Result<Void>> handleRateLimit(RateLimitException e) {
        log.warn("触发限流", e);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Result.fail(HttpStatus.TOO_MANY_REQUESTS.value(), e.getMessage()));
    }

    @ExceptionHandler(FileUploadException.class)
    public ResponseEntity<Result<Void>> handleFileUpload(FileUploadException e) {
        if (e.getType() == FileUploadException.Type.IO_ERROR) {
            log.error("文件上传 IO 异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), "文件上传失败"));
        }
        log.warn("文件上传校验失败", e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(HttpStatus.BAD_REQUEST.value(), e.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("数据完整性冲突", e);
        String msg = e.getMostSpecificCause().getMessage();
        if (msg.contains("duplicate") || msg.contains("unique") || msg.contains("重复")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.fail(HttpStatus.CONFLICT.value(), "数据已存在"));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(HttpStatus.BAD_REQUEST.value(), "数据冲突，请检查输入"));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result<Void>> handleRuntime(RuntimeException e) {
        log.error("发生未捕获异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务器内部错误"));
    }
}
