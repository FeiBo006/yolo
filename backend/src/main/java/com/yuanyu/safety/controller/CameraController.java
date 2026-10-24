package com.yuanyu.safety.controller;

import com.yuanyu.safety.common.Result;
import com.yuanyu.safety.entity.Camera;
import com.yuanyu.safety.service.CameraService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cameras")
@RequiredArgsConstructor
public class CameraController {

    private final CameraService cameraService;
    private final RestTemplate restTemplate;

    @Value("${app.ai-base-url}")
    private String aiBaseUrl;

    @GetMapping
    public Result<List<Camera>> list(@RequestParam(required = false) Long siteId,
                                     @RequestParam(required = false) String keyword) {
        return Result.ok(cameraService.list(siteId, keyword));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Result<Camera> create(@RequestBody Camera camera) {
        return Result.ok(cameraService.create(camera));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Result<Camera> update(@PathVariable Long id, @RequestBody Camera camera) {
        return Result.ok(cameraService.update(id, camera));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        cameraService.delete(id);
        return Result.ok();
    }

    /**
     * 通过算法服务探测视频源是否可用。
     */
    @PostMapping("/{id}/test")
    public Result<Map<String, Object>> test(@PathVariable Long id) {
        Camera camera = cameraService.get(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("streamType", camera.getStreamType());
        body.put("streamUrl", camera.getStreamUrl());
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    aiBaseUrl + "/ai/test-source", body, Map.class);
            return Result.ok(response.getBody() == null ? Map.of("ok", false) : response.getBody());
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("ok", false);
            fallback.put("message", "算法服务未启动或无法连接：" + e.getMessage());
            return Result.ok(fallback);
        }
    }
}
