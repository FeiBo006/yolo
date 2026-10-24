package com.yuanyu.safety.controller;

import com.yuanyu.safety.common.Result;
import com.yuanyu.safety.entity.DetectionRecord;
import com.yuanyu.safety.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/detections")
@RequiredArgsConstructor
public class DetectionController {

    private final DashboardService dashboardService;

    @GetMapping("/latest")
    public Result<DetectionRecord> latest(@RequestParam(required = false) Long cameraId) {
        return Result.ok(dashboardService.latestDetection(cameraId));
    }

    @GetMapping("/recent")
    public Result<List<DetectionRecord>> recent(@RequestParam(required = false) Long cameraId,
                                                @RequestParam(defaultValue = "24") int limit) {
        return Result.ok(dashboardService.recentDetections(cameraId, limit));
    }
}
