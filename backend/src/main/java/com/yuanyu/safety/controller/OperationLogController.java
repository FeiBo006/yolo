package com.yuanyu.safety.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuanyu.safety.common.PageResult;
import com.yuanyu.safety.common.Result;
import com.yuanyu.safety.entity.OperationLog;
import com.yuanyu.safety.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class OperationLogController {

    private final OperationLogMapper operationLogMapper;

    @GetMapping
    public Result<PageResult<OperationLog>> page(@RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size) {
        var wrapper = Wrappers.<OperationLog>lambdaQuery()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(OperationLog::getUsername, keyword).or()
                        .like(OperationLog::getModule, keyword).or()
                        .like(OperationLog::getOperation, keyword))
                .orderByDesc(OperationLog::getId);
        Page<OperationLog> result = operationLogMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.ok(new PageResult<>(result.getRecords(), result.getTotal(), page, size));
    }
}
