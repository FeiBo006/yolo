package com.yuanyu.safety.controller;

import com.yuanyu.safety.common.Result;
import com.yuanyu.safety.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.ok(dashboardService.summary());
    }

    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(dashboardService.trend(days));
    }

    @GetMapping("/ranking")
    public Result<List<Map<String, Object>>> ranking(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(dashboardService.ranking(days));
    }

    @GetMapping("/status")
    public Result<List<Map<String, Object>>> status(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(dashboardService.statusDistribution(days));
    }
}
