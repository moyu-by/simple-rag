package org.moyu.rag.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息历史。
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kbId;

    private Long userId;

    /** 'user' 或 'assistant' */
    private String role;

    private String content;

    /** AI 回答的引用来源，JSON 数组 */
    private String sources;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
