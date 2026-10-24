package com.yuanyu.safety.controller;

import com.yuanyu.safety.common.PageResult;
import com.yuanyu.safety.common.Result;
import com.yuanyu.safety.dto.AlarmHandleRequest;
import com.yuanyu.safety.entity.Alarm;
import com.yuanyu.safety.security.LoginUser;
import com.yuanyu.safety.service.AlarmService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/alarms")
@RequiredArgsConstructor
public class AlarmController {

    private final AlarmService alarmService;

    @GetMapping
    public Result<PageResult<Alarm>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String behaviorType,
            @RequestParam(required = false) Long cameraId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(alarmService.page(status, behaviorType, cameraId, keyword, start, end, page, size));
    }

    @GetMapping("/{id}")
    public Result<Alarm> detail(@PathVariable Long id) {
        return Result.ok(alarmService.get(id));
    }

    @PutMapping("/{id}/handle")
    public Result<Alarm> handle(@PathVariable Long id,
                                @Valid @RequestBody AlarmHandleRequest request,
                                @AuthenticationPrincipal LoginUser user) {
        return Result.ok(alarmService.handle(id, request, user));
    }

    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String behaviorType,
            @RequestParam(required = false) Long cameraId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end,
            HttpServletResponse response) {
        alarmService.export(status, behaviorType, cameraId, keyword, start, end, response);
    }
}
