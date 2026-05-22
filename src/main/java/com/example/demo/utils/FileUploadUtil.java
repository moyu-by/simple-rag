package com.example.demo.utils;

import com.example.demo.exception.FileUploadException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 通用文件上传工具类。
 * <p>
 * 将 {@link MultipartFile} 或本地 {@link File} 写入磁盘，返回外网可访问 URL。
 * 内置校验：文件大小、扩展名白名单、MIME 类型白名单；支持覆盖/原子写入两种策略。
 * </p>
 *
 * <h3>快速入门</h3>
 * 
 * <pre>{@code
 * // 一行上传
 * String url = FileUploadUtils.uploadFile(file, uploadDir, urlPrefix);
 *
 * // 带校验
 * var config = UploadConfig.builder()
 *         .allowedExtensions(IMAGE_EXTENSIONS)
 *         .maxFileSize(5 * 1024 * 1024)
 *         .build();
 * String url = FileUploadUtils.uploadFile(file, uploadDir, urlPrefix, config);
 * }</pre>
 *
 * <h3>本地文件</h3>
 * 
 * <pre>{@code
 * String url = FileUploadUtils.saveLocalFile(srcFile, uploadDir, urlPrefix);
 * }</pre>
 *
 * @see UploadConfig
 * @see FileUploadException
 */
public class FileUploadUtil {
    // ==================== 默认常量 ====================
    /** 单文件默认上限：10 MB */
    public static final long DEFAULT_MAX_FILE_SIZE = 10 * 1024 * 1024;
    /** 常用图片扩展名 */
    public static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "ico"
    );
    /** 常用文档扩展名 */
    public static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md"
    );
    // ==================== 上传 API（MultipartFile） ====================
    /**
     * 上传文件（使用默认配置）。
     *
     * @param file      上传的文件
     * @param uploadDir 目标存储目录（绝对路径），不会自动追加子目录
     * @param urlPrefix URL 访问前缀（需以 / 结尾，或自行决定格式）
     * @return 文件的外部可访问 URL
     */
    public static String uploadFile(MultipartFile file, Path uploadDir, String urlPrefix) {
        return uploadFile(file, uploadDir, urlPrefix, UploadConfig.defaultConfig());
    }
    /**
     * 上传文件（自定义配置）。
     *
     * @param file      上传的文件
     * @param uploadDir 目标存储目录（绝对路径）
     * @param urlPrefix URL 访问前缀
     * @param config    上传配置（校验规则、覆盖策略等）
     * @return 文件的外部可访问 URL
     */
    public static String uploadFile(MultipartFile file, Path uploadDir, String urlPrefix, UploadConfig config) {
        // ── 前置校验 ──
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(uploadDir, "uploadDir must not be null");
        Objects.requireNonNull(config, "config must not be null");
        if (file.isEmpty()) {
            throw new FileUploadException(FileUploadException.Type.EMPTY_FILE, "Cannot upload empty file");
        }
        // ── 原始文件名（null 安全） ──
        String original = file.getOriginalFilename();                 // 可能为 null
        if (original == null || original.isBlank()) {
            original = "unknown";
        }
        // ── 扩展名提取（无后缀时返回空字符串） ──
        String extension = extractExtension(original);
        // ── 扩展名校验 ──
        if (!config.allowedExtensions.isEmpty()) {
            checkExtension(extension, config.allowedExtensions);
        }
        // ── MIME 类型校验（可选，文件上传时可从 request 感知） ──
        if (!config.allowedMimeTypes.isEmpty()) {
            String mimeType = file.getContentType();                  // 可能为 null
            checkMimeType(mimeType, config.allowedMimeTypes);
        }
        // ── 文件大小校验 ──
        if (file.getSize() > config.maxFileSize) {
            throw new FileUploadException(FileUploadException.Type.FILE_TOO_LARGE, String.format(
                    "File size %d bytes exceeds limit of %d bytes", file.getSize(), config.maxFileSize));
        }
        // ── 确保目录存在 ──
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new FileUploadException(FileUploadException.Type.IO_ERROR,
                    "Failed to create upload directory: " + uploadDir, e);
        }
        // ── 生成唯一文件名并写入 ──
        String fileName = generateUniqueName(extension);
        Path dest = uploadDir.resolve(fileName);
        try {
            if (config.overwrite) {
                file.transferTo(dest.toFile());
            } else {
                // 原子写入：写临时文件 → 重命名，防止不完整写入
                Path temp = Files.createTempFile(uploadDir, ".upload_", "." + (extension.isEmpty() ? "tmp" : extension));
                try {
                    file.transferTo(temp.toFile());
                    Files.move(temp, dest, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException ex) {
                    Files.deleteIfExists(temp);
                    throw ex;
                }
            }
        } catch (IOException e) {
            throw new FileUploadException(FileUploadException.Type.IO_ERROR,
                    "Failed to write file: " + fileName, e);
        }
        // ── 返回 URL ──
        return urlPrefix + fileName;
    }
    // ==================== 保存 API（已存在的本地文件） ====================
    /**
     * 将已存在的本地文件复制到存储目录（使用默认配置）。
     *
     * @param srcFile   源文件（必须在磁盘上真实存在）
     * @param uploadDir 目标存储目录
     * @param urlPrefix URL 访问前缀
     * @return 文件的外部可访问 URL
     */
    public static String saveLocalFile(File srcFile, Path uploadDir, String urlPrefix) {
        return saveLocalFile(srcFile, uploadDir, urlPrefix, UploadConfig.defaultConfig());
    }
    /**
     * 将已存在的本地文件复制到存储目录（自定义配置）。
     *
     * @param srcFile   源文件（必须在磁盘上真实存在）
     * @param uploadDir 目标存储目录
     * @param urlPrefix URL 访问前缀
     * @param config    上传配置
     * @return 文件的外部可访问 URL
     */
    public static String saveLocalFile(File srcFile, Path uploadDir, String urlPrefix, UploadConfig config) {
        // ── 前置校验 ──
        Objects.requireNonNull(srcFile, "srcFile must not be null");
        Objects.requireNonNull(uploadDir, "uploadDir must not be null");
        Objects.requireNonNull(config, "config must not be null");
        if (!srcFile.exists()) {
            throw new FileUploadException(FileUploadException.Type.FILE_NOT_EXIST,
                    "Source file does not exist: " + srcFile.getAbsolutePath());
        }
        if (!srcFile.isFile()) {
            throw new FileUploadException(FileUploadException.Type.NOT_A_FILE,
                    "Source path is not a file: " + srcFile.getAbsolutePath());
        }
        // ── 文件名 & 扩展名 ──
        String original = srcFile.getName();
        String extension = extractExtension(original);
        // ── 扩展名校验 ──
        if (!config.allowedExtensions.isEmpty()) {
            checkExtension(extension, config.allowedExtensions);
        }
        // ── 大小校验 ──
        long size;
        try {
            size = Files.size(srcFile.toPath());
        } catch (IOException e) {
            throw new FileUploadException(FileUploadException.Type.IO_ERROR,
                    "Failed to read source file size: " + srcFile.getAbsolutePath(), e);
        }
        if (size > config.maxFileSize) {
            throw new FileUploadException(FileUploadException.Type.FILE_TOO_LARGE, String.format(
                    "File size %d bytes exceeds limit of %d bytes", size, config.maxFileSize));
        }
        // ── 确保目录存在并复制 ──
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new FileUploadException(FileUploadException.Type.IO_ERROR,
                    "Failed to create upload directory: " + uploadDir, e);
        }
        // ── 生成唯一文件名并复制 ──
        String fileName = generateUniqueName(extension);
        Path dest = uploadDir.resolve(fileName);
        try {
            if (config.overwrite) {
                Files.copy(srcFile.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(srcFile.toPath(), dest);
            }
        } catch (IOException e) {
            throw new FileUploadException(FileUploadException.Type.IO_ERROR,
                    "Failed to copy file to: " + dest, e);
        }
        return urlPrefix + fileName;
    }
    // ==================== 内部工具方法 ====================
    /**
     * 从文件名中提取扩展名（不含 dot），无扩展名时返回空字符串。
     */
    private static String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot == -1 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase();
    }
    /**
     * 生成唯一文件名：UUID + 扩展名。
     */
    private static String generateUniqueName(String extension) {
        return extension.isEmpty()
                ? UUID.randomUUID().toString()
                : UUID.randomUUID() + "." + extension;
    }
    /**
     * 校验扩展名是否在白名单内。
     */
    private static void checkExtension(String extension, Set<String> allowed) {
        if (extension.isEmpty()) {
            throw new FileUploadException(FileUploadException.Type.NO_EXTENSION,
                    "File has no extension, which is not allowed");
        }
        if (!allowed.contains(extension)) {
            throw new FileUploadException(FileUploadException.Type.EXTENSION_NOT_ALLOWED,
                    "File extension '." + extension + "' is not allowed. Allowed: " + allowed);
        }
    }
    /**
     * 校验 MIME 类型是否在白名单内。
     */
    private static void checkMimeType(String mimeType, Set<String> allowed) {
        if (mimeType == null || mimeType.isBlank()) {
            throw new FileUploadException(FileUploadException.Type.MIME_TYPE_NOT_ALLOWED,
                    "Cannot determine file MIME type");
        }
        if (!allowed.contains(mimeType)) {
            throw new FileUploadException(FileUploadException.Type.MIME_TYPE_NOT_ALLOWED,
                    "MIME type '" + mimeType + "' is not allowed. Allowed: " + allowed);
        }
    }
    // ==================== 配置类（Lombok Builder） ====================
    /**
     * 文件上传配置。推荐用 {@link UploadConfigBuilder} 构建。
     */
    @Data
    @Builder
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class UploadConfig {
        @Builder.Default private Set<String> allowedExtensions = Set.of();
        @Builder.Default private Set<String> allowedMimeTypes = Set.of();
        @Builder.Default private long maxFileSize = DEFAULT_MAX_FILE_SIZE;
        @Builder.Default private boolean overwrite = true;

        public static UploadConfig defaultConfig() {
            return builder().build();
        }
    }
}