package com.yuanyu.safety.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 算法服务周期性上报的检测指标。
 */
@Data
public class DetectionReport {

    private Long cameraId;

    private LocalDateTime recordTime;

    private Integer personCount;

    private Integer helmetCount;

    private Integer noHelmetCount;

    private Integer vestCount;

    private Integer noVestCount;

    private BigDecimal fps;

    private BigDecimal inferenceMs;

    private String modelVersion;
}
