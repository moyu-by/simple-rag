package com.example.demo.exception;

import lombok.Getter;

/**
 * 文件上传/校验相关的自定义异常。
 * <p>通过 {@link Type} 枚举区分不同的错误场景。</p>
 */
@Getter
public class FileUploadException extends RuntimeException {

    /**
     * 文件上传错误类型。
     */
    public enum Type {
        /** 上传的文件为空 */
        EMPTY_FILE,
        /** 源文件不存在 */
        FILE_NOT_EXIST,
        /** 源路径不是文件 */
        NOT_A_FILE,
        /** 文件无扩展名（且不允许无扩展名） */
        NO_EXTENSION,
        /** 扩展名不在白名单内 */
        EXTENSION_NOT_ALLOWED,
        /** MIME 类型不在白名单内 */
        MIME_TYPE_NOT_ALLOWED,
        /** 文件大小超过上限 */
        FILE_TOO_LARGE,
        /** IO 读写异常 */
        IO_ERROR
    }

    private final Type type;

    public FileUploadException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public FileUploadException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }
}
