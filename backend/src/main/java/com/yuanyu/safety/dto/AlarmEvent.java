package com.yuanyu.safety.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 算法服务上报的告警事件。
 */
@Data
public class AlarmEvent {

    private String alarmNo;

    private Long cameraId;

    private String behaviorType;

    private String level;

    private BigDecimal confidence;

    private Integer trackId;

    private String bbox;

    private String snapshotUrl;

    private LocalDateTime alarmTime;
}
