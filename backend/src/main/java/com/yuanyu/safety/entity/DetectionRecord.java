package com.yuanyu.safety.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("biz_detection_record")
public class DetectionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

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
