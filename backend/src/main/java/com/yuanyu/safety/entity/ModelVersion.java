package com.yuanyu.safety.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("biz_model")
public class ModelVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String modelName;

    private String version;

    private String filePath;

    private BigDecimal map50;

    private BigDecimal precisionRate;

    private BigDecimal recallRate;

    /** ACTIVE / INACTIVE */
    private String status;

    private LocalDateTime deployTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
