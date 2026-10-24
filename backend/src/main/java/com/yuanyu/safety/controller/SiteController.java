package com.yuanyu.safety.controller;

import com.yuanyu.safety.common.Result;
import com.yuanyu.safety.entity.Site;
import com.yuanyu.safety.service.SiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sites")
@RequiredArgsConstructor
public class SiteController {

    private final SiteService siteService;

    @GetMapping
    public Result<List<Site>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(siteService.list(keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Result<Site> detail(@PathVariable Long id) {
        return Result.ok(siteService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Result<Site> create(@RequestBody Site site) {
        return Result.ok(siteService.create(site));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Result<Site> update(@PathVariable Long id, @RequestBody Site site) {
        return Result.ok(siteService.update(id, site));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        siteService.delete(id);
        return Result.ok();
    }
}
