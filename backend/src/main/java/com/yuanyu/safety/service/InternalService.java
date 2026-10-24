package com.yuanyu.safety.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yuanyu.safety.common.BizException;
import com.yuanyu.safety.dto.AlarmEvent;
import com.yuanyu.safety.dto.DetectionReport;
import com.yuanyu.safety.entity.Alarm;
import com.yuanyu.safety.entity.Area;
import com.yuanyu.safety.entity.Camera;
import com.yuanyu.safety.entity.DetectionRecord;
import com.yuanyu.safety.entity.Rule;
import com.yuanyu.safety.mapper.AreaMapper;
import com.yuanyu.safety.mapper.CameraMapper;
import com.yuanyu.safety.mapper.DetectionRecordMapper;
import com.yuanyu.safety.mapper.RuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 算法服务内部接口：拉取配置、上报告警与指标。
 */
@Service
@RequiredArgsConstructor
public class InternalService {

    private final CameraMapper cameraMapper;
    private final AreaMapper areaMapper;
    private final RuleMapper ruleMapper;
    private final DetectionRecordMapper detectionRecordMapper;
    private final AlarmService alarmService;
    private final CameraService cameraService;

    @Value("${app.internal-token}")
    private String internalToken;

    public void checkToken(String token) {
        if (token == null || !token.equals(internalToken)) {
            throw new BizException(401, "内部调用凭证无效");
        }
    }

    public Map<String, Object> config() {
        List<Camera> cameras = cameraMapper.selectList(Wrappers.<Camera>lambdaQuery()
                .eq(Camera::getEnabled, 1));
        List<Area> areas = areaMapper.selectList(Wrappers.<Area>lambdaQuery()
                .eq(Area::getEnabled, 1));
        List<Rule> rules = ruleMapper.selectList(Wrappers.<Rule>lambdaQuery()
                .eq(Rule::getEnabled, 1));

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("cameras", cameras);
        config.put("areas", areas);
        config.put("rules", rules);
        config.put("serverTime", LocalDateTime.now());
        return config;
    }

    public Alarm reportAlarm(AlarmEvent event) {
        return alarmService.ingest(event);
    }

    public void reportDetection(DetectionReport report) {
        DetectionRecord record = new DetectionRecord();
        record.setCameraId(report.getCameraId());
        record.setRecordTime(report.getRecordTime() == null ? LocalDateTime.now() : report.getRecordTime());
        record.setPersonCount(nvl(report.getPersonCount()));
        record.setHelmetCount(nvl(report.getHelmetCount()));
        record.setNoHelmetCount(nvl(report.getNoHelmetCount()));
        record.setVestCount(nvl(report.getVestCount()));
        record.setNoVestCount(nvl(report.getNoVestCount()));
        record.setFps(report.getFps() == null ? BigDecimal.ZERO : report.getFps());
        record.setInferenceMs(report.getInferenceMs() == null ? BigDecimal.ZERO : report.getInferenceMs());
        record.setModelVersion(report.getModelVersion());
        detectionRecordMapper.insert(record);
    }

    public void heartbeat(Long cameraId) {
        if (cameraId != null) {
            cameraService.heartbeat(cameraId);
        }
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
