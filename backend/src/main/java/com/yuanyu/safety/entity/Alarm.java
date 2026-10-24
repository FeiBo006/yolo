package com.yuanyu.safety.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("biz_alarm")
public class Alarm {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String alarmNo;

    private Long cameraId;

    private String cameraName;

    private Long siteId;

    private String behaviorType;

    private String level;

    private BigDecimal confidence;

    private Integer trackId;

    /** 百分比坐标：x1,y1,x2,y2 */
    private String bbox;

    private String snapshotUrl;

    private LocalDateTime alarmTime;

    /** PENDING / CONFIRMED / REPAIRED / FALSE_ALARM */
    private String status;

    private Long handlerId;

    private String handlerName;

    private LocalDateTime handleTime;

    private String handleRemark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
