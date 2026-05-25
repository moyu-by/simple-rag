package org.moyu.rag.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型配置（LLM 提供商 API 配置）。
 */
@Data
@TableName(value = "model_config", autoResultMap = true)
public class ModelConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kbId;

    private String name;

    /** 模型类型: EMBEDDING / CHAT */
    private String modelType;

    /** openai / anthropic / azure / custom */
    private String provider;

    /** 兼容 API 的 base_url，标准 API 可为空 */
    private String baseUrl;

    /** 加密存储 */
    private String apiKey;

    private String modelName;

    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private Object parameters;

    private Boolean isActive;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
