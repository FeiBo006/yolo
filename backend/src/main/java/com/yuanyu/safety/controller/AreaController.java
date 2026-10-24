package com.yuanyu.safety.controller;

import com.yuanyu.safety.common.Result;
import com.yuanyu.safety.entity.Area;
import com.yuanyu.safety.service.AreaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/areas")
@RequiredArgsConstructor
public class AreaController {

    private final AreaService areaService;

    @GetMapping
    public Result<List<Area>> list(@RequestParam(required = false) Long cameraId) {
        return Result.ok(areaService.list(cameraId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Result<Area> create(@RequestBody Area area) {
        return Result.ok(areaService.create(area));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Result<Area> update(@PathVariable Long id, @RequestBody Area area) {
        return Result.ok(areaService.update(id, area));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Result<Void> delete(@PathVariable Long id) {
        areaService.delete(id);
        return Result.ok();
    }
}
