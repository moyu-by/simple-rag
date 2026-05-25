package org.moyu.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.moyu.rag.common.ContextUtil;
import org.moyu.rag.config.FileProperties;
import org.moyu.rag.dto.DocumentResponse;
import org.moyu.rag.entity.Document;
import org.moyu.rag.entity.KbMembership;
import org.moyu.rag.enums.KbRole;
import org.moyu.rag.exception.AuthException;
import org.moyu.rag.exception.FileUploadException;
import org.moyu.rag.mapper.DocumentMapper;
import org.moyu.rag.mapper.KbMembershipMapper;
import org.moyu.rag.service.DocumentProcessor;
import org.moyu.rag.service.DocumentService;
import org.moyu.rag.utils.FileUploadUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentMapper documentMapper;
    private final KbMembershipMapper membershipMapper;
    private final FileProperties fileProperties;
    private final DocumentProcessor documentProcessor;
    private final Executor taskExecutor;

    @Override
    public DocumentResponse upload(Long kbId, MultipartFile file) {
        return doUpload(kbId, file, null);
    }

    @Override
    public DocumentResponse upload(Long kbId, MultipartFile file, Long embeddingConfigId) {
        return doUpload(kbId, file, embeddingConfigId);
    }

    private DocumentResponse doUpload(Long kbId, MultipartFile file, Long embeddingConfigId) {
        Long userId = ContextUtil.getUserId();
        requireAdmin(userId, kbId);

        // 1. 计算文件 MD5，用于内容去重
        String md5;
        try {
            md5 = DigestUtils.md5DigestAsHex(file.getBytes());
        } catch (IOException e) {
            throw new FileUploadException(FileUploadException.Type.IO_ERROR, "读取文件失败", e);
        }

        // 2. 检查同一知识库下是否已存在相同内容的文件
        Document existing = documentMapper.selectOne(
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getKbId, kbId)
                        .eq(Document::getFileMd5, md5)
                        .last("LIMIT 1"));
        if (existing != null) {
            throw new FileUploadException(
                    FileUploadException.Type.DUPLICATE_FILE,
                    "文件已存在（" + existing.getFileName() + "），请勿重复上传");
        }

        // 3. 保存文件到磁盘
        Path uploadDir = Path.of(fileProperties.getStorePath());
        FileUploadUtil.UploadConfig config = FileUploadUtil.UploadConfig.builder()
                .allowedExtensions(FileUploadUtil.DOCUMENT_EXTENSIONS)
                .overwrite(false)
                .build();

        String fullUrl = FileUploadUtil.uploadFile(file, uploadDir, fileProperties.getUrlPrefix(), config);
        String storedPath = fullUrl.substring(fullUrl.lastIndexOf('/') + 1);

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        }

        // 4. 写入数据库，携带 MD5
        Document doc = new Document();
        doc.setKbId(kbId);
        doc.setFileName(originalName != null ? originalName : "unknown");
        doc.setFilePath(storedPath);
        doc.setFileSize(file.getSize());
        doc.setFileType(ext);
        doc.setFileMd5(md5);
        doc.setStatus(0);
        doc.setUploadedBy(userId);
        documentMapper.insert(doc);

        // 5. 如果指定了 embedding 配置，异步触发处理，不阻塞上传响应
        if (embeddingConfigId != null) {
            final Long finalDocId = doc.getId();
            taskExecutor.execute(() -> documentProcessor.process(kbId, finalDocId, embeddingConfigId));
        }

        return toResponse(doc);
    }

    @Override
    public List<DocumentResponse> list(Long kbId) {
        Long userId = ContextUtil.getUserId();
        requireMembership(userId, kbId);

        List<Document> docs = documentMapper.selectList(
                new LambdaQueryWrapper<Document>().eq(Document::getKbId, kbId)
                        .orderByDesc(Document::getCreateTime));

        return docs.stream().map(this::toResponse).toList();
    }

    @Override
    public DocumentResponse getById(Long kbId, Long docId) {
        Long userId = ContextUtil.getUserId();
        requireMembership(userId, kbId);

        Document doc = documentMapper.selectById(docId);
        if (doc == null || !doc.getKbId().equals(kbId)) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "文档不存在");
        }
        return toResponse(doc);
    }

    @Override
    public void delete(Long kbId, Long docId) {
        Long userId = ContextUtil.getUserId();
        requireAdmin(userId, kbId);

        Document doc = documentMapper.selectById(docId);
        if (doc == null || !doc.getKbId().equals(kbId)) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "文档不存在");
        }

        try {
            Path diskFile = Path.of(fileProperties.getStorePath(), doc.getFilePath());
            Files.deleteIfExists(diskFile);
        } catch (IOException e) {
            log.warn("删除磁盘文件失败: {}", doc.getFilePath(), e);
        }

        documentMapper.deleteById(docId);
    }

    // ==================== 内部方法 ====================

    private DocumentResponse toResponse(Document doc) {
        return new DocumentResponse(
                doc.getId(), doc.getKbId(), doc.getFileName(),
                fileProperties.getUrlPrefix() + doc.getFilePath(),
                doc.getFileSize(), doc.getFileType(), doc.getStatus(),
                doc.getChunkCount(), doc.getUploadedBy(), doc.getCreateTime());
    }

    private void requireMembership(Long userId, Long kbId) {
        if (membershipMapper.selectCount(
                new LambdaQueryWrapper<KbMembership>()
                        .eq(KbMembership::getUserId, userId)
                        .eq(KbMembership::getKbId, kbId)) == 0) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "无权限访问该知识库");
        }
    }

    private void requireAdmin(Long userId, Long kbId) {
        KbMembership m = membershipMapper.selectOne(
                new LambdaQueryWrapper<KbMembership>()
                        .eq(KbMembership::getUserId, userId)
                        .eq(KbMembership::getKbId, kbId));
        if (m == null || !m.getRoleInKb().atLeast(KbRole.ADMIN)) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "权限不足");
        }
    }
}
