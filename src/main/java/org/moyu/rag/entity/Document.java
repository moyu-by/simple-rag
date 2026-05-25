package org.moyu.rag.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档（上传文件）。
 */
@Data
@TableName("document")
public class Document {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kbId;

    private String fileName;

    private String filePath;

    private Long fileSize;

    private String fileType;

    /** 0=处理中, 1=就绪, 2=失败 */
    private Integer status;

    /** 向量化后的 chunk 数量 */
    private Integer chunkCount;

    private Long uploadedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
