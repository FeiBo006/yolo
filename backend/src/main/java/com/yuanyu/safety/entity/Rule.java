package com.yuanyu.safety.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("biz_rule")
public class Rule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleName;

    /** NO_HELMET / NO_VEST / INTRUSION */
    private String behaviorType;

    /** 为空表示全局规则 */
    private Long cameraId;

    private BigDecimal minConfidence;

    private Integer consecutiveFrames;

    private Integer cooldownSeconds;

    /** LOW / MEDIUM / HIGH */
    private String level;

    /** HH:mm，空表示全天生效 */
    private String startTime;

    private String endTime;

    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String cameraName;
}
