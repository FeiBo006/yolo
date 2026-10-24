package com.yuanyu.safety.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AlarmHandleRequest {

    /** CONFIRMED / REPAIRED / FALSE_ALARM */
    @NotBlank(message = "处置状态不能为空")
    private String status;

    private String remark;
}
