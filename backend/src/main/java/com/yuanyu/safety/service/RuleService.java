package com.yuanyu.safety.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yuanyu.safety.common.BizException;
import com.yuanyu.safety.entity.Camera;
import com.yuanyu.safety.entity.Rule;
import com.yuanyu.safety.mapper.CameraMapper;
import com.yuanyu.safety.mapper.RuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RuleService {

    private final RuleMapper ruleMapper;
    private final CameraMapper cameraMapper;

    public List<Rule> list(Long cameraId, String behaviorType) {
        List<Rule> rules = ruleMapper.selectList(Wrappers.<Rule>lambdaQuery()
                .eq(cameraId != null, Rule::getCameraId, cameraId)
                .eq(StringUtils.hasText(behaviorType), Rule::getBehaviorType, behaviorType)
                .orderByDesc(Rule::getId));
        rules.forEach(r -> {
            if (r.getCameraId() != null) {
                Camera camera = cameraMapper.selectById(r.getCameraId());
                r.setCameraName(camera == null ? null : camera.getCameraName());
            }
        });
        return rules;
    }

    public Rule create(Rule rule) {
        normalize(rule);
        rule.setId(null);
        rule.setEnabled(rule.getEnabled() == null ? 1 : rule.getEnabled());
        ruleMapper.insert(rule);
        return rule;
    }

    public Rule update(Long id, Rule input) {
        Rule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(404, "规则不存在");
        }
        normalize(input);
        rule.setRuleName(input.getRuleName());
        rule.setBehaviorType(input.getBehaviorType());
        rule.setCameraId(input.getCameraId());
        rule.setMinConfidence(input.getMinConfidence());
        rule.setConsecutiveFrames(input.getConsecutiveFrames());
        rule.setCooldownSeconds(input.getCooldownSeconds());
        rule.setLevel(input.getLevel());
        rule.setStartTime(input.getStartTime());
        rule.setEndTime(input.getEndTime());
        if (input.getEnabled() != null) {
            rule.setEnabled(input.getEnabled());
        }
        ruleMapper.updateById(rule);
        return rule;
    }

    public void delete(Long id) {
        ruleMapper.deleteById(id);
    }

    private void normalize(Rule rule) {
        if (!StringUtils.hasText(rule.getRuleName())) {
            throw new BizException("规则名称不能为空");
        }
        if (!StringUtils.hasText(rule.getBehaviorType())) {
            throw new BizException("行为类型不能为空");
        }
        if (rule.getMinConfidence() == null) {
            rule.setMinConfidence(new BigDecimal("0.450"));
        }
        if (rule.getConsecutiveFrames() == null || rule.getConsecutiveFrames() < 1) {
            rule.setConsecutiveFrames(3);
        }
        if (rule.getCooldownSeconds() == null || rule.getCooldownSeconds() < 0) {
            rule.setCooldownSeconds(60);
        }
        if (!StringUtils.hasText(rule.getLevel())) {
            rule.setLevel("MEDIUM");
        }
    }
}
