package com.yuanyu.safety.controller;

import com.yuanyu.safety.common.Result;
import com.yuanyu.safety.entity.Rule;
import com.yuanyu.safety.service.RuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class RuleController {

    private final RuleService ruleService;

    @GetMapping
    public Result<List<Rule>> list(@RequestParam(required = false) Long cameraId,
                                   @RequestParam(required = false) String behaviorType) {
        return Result.ok(ruleService.list(cameraId, behaviorType));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Rule> create(@RequestBody Rule rule) {
        return Result.ok(ruleService.create(rule));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Rule> update(@PathVariable Long id, @RequestBody Rule rule) {
        return Result.ok(ruleService.update(id, rule));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return Result.ok();
    }
}
