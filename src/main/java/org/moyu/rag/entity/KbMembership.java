package org.moyu.rag.entity;

import com.baomidou.mybatisplus.annotation.*;
import org.moyu.rag.enums.KbRole;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库成员关系（多对多）。
 *
 * <p>一个用户可加入多个知识库，在每个知识库内有独立的角色。</p>
 */
@Data
@TableName("kb_membership")
public class KbMembership {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long kbId;

    /** 在此知识库内的角色: 0=MEMBER, 1=ADMIN, 2=BOSS */
    private KbRole roleInKb;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
