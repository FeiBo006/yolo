package com.yuanyu.safety.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_camera")
public class Camera {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long siteId;

    private String cameraName;

    private String streamUrl;

    /** RTSP / VIDEO / USB / DEMO */
    private String streamType;

    private String location;

    private Integer enabled;

    private LocalDateTime lastHeartbeat;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Integer deleted;

    @TableField(exist = false)
    private String siteName;

    @TableField(exist = false)
    private Boolean online;
}
