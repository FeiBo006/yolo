package com.yuanyu.safety.controller;

import com.yuanyu.safety.common.Result;
import com.yuanyu.safety.dto.AlarmEvent;
import com.yuanyu.safety.dto.DetectionReport;
import com.yuanyu.safety.entity.Alarm;
import com.yuanyu.safety.service.InternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 仅供算法服务调用，通过 X-Internal-Token 校验。
 */
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalController {

    private final InternalService internalService;

    @GetMapping("/config")
    public Result<Map<String, Object>> config(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        internalService.checkToken(token);
        return Result.ok(internalService.config());
    }

    @PostMapping("/alarms")
    public Result<Map<String, Object>> alarm(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody AlarmEvent event) {
        internalService.checkToken(token);
        Alarm alarm = internalService.reportAlarm(event);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", alarm != null);
        result.put("alarmNo", event.getAlarmNo());
        result.put("duplicated", alarm == null);
        return Result.ok(result);
    }

    @PostMapping("/detections")
    public Result<Void> detection(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody DetectionReport report) {
        internalService.checkToken(token);
        internalService.reportDetection(report);
        return Result.ok();
    }

    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam Long cameraId) {
        internalService.checkToken(token);
        internalService.heartbeat(cameraId);
        return Result.ok();
    }
}
