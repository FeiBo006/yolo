package com.yuanyu.safety.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_area")
public class Area {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long cameraId;

    private String areaName;

    /** DANGER / SAFE */
    private String areaType;

    /** 百分比坐标 JSON：[[0.1,0.2],[0.3,0.4]] */
    private String points;

    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String cameraName;
}
