package com.yuanyu.safety.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yuanyu.safety.common.Result;
import com.yuanyu.safety.entity.ModelVersion;
import com.yuanyu.safety.mapper.ModelVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelController {

    private final ModelVersionMapper modelVersionMapper;

    @GetMapping
    public Result<List<ModelVersion>> list() {
        return Result.ok(modelVersionMapper.selectList(
                Wrappers.<ModelVersion>lambdaQuery().orderByDesc(ModelVersion::getId)));
    }
}
