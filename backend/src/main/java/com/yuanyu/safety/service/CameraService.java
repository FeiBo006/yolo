package com.yuanyu.safety.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yuanyu.safety.common.BizException;
import com.yuanyu.safety.entity.Camera;
import com.yuanyu.safety.entity.Site;
import com.yuanyu.safety.mapper.CameraMapper;
import com.yuanyu.safety.mapper.SiteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CameraService {

    private final CameraMapper cameraMapper;
    private final SiteMapper siteMapper;

    public List<Camera> list(Long siteId, String keyword) {
        List<Camera> cameras = cameraMapper.selectList(Wrappers.<Camera>lambdaQuery()
                .eq(siteId != null, Camera::getSiteId, siteId)
                .like(StringUtils.hasText(keyword), Camera::getCameraName, keyword)
                .orderByDesc(Camera::getId));
        Map<Long, String> siteNames = siteMapper.selectList(null).stream()
                .collect(Collectors.toMap(Site::getId, Site::getSiteName, (a, b) -> a));
        LocalDateTime onlineLine = LocalDateTime.now().minusMinutes(2);
        cameras.forEach(c -> {
            c.setSiteName(siteNames.get(c.getSiteId()));
            c.setOnline(c.getLastHeartbeat() != null && c.getLastHeartbeat().isAfter(onlineLine));
        });
        return cameras;
    }

    public Camera get(Long id) {
        Camera camera = cameraMapper.selectById(id);
        if (camera == null) {
            throw new BizException(404, "摄像头不存在");
        }
        return camera;
    }

    public Camera create(Camera camera) {
        validate(camera);
        camera.setId(null);
        camera.setEnabled(camera.getEnabled() == null ? 1 : camera.getEnabled());
        camera.setStreamType(StringUtils.hasText(camera.getStreamType()) ? camera.getStreamType() : "DEMO");
        cameraMapper.insert(camera);
        return camera;
    }

    public Camera update(Long id, Camera input) {
        Camera camera = get(id);
        validate(input);
        camera.setSiteId(input.getSiteId());
        camera.setCameraName(input.getCameraName());
        camera.setStreamUrl(input.getStreamUrl());
        camera.setStreamType(input.getStreamType());
        camera.setLocation(input.getLocation());
        if (input.getEnabled() != null) {
            camera.setEnabled(input.getEnabled());
        }
        cameraMapper.updateById(camera);
        return camera;
    }

    public void delete(Long id) {
        get(id);
        cameraMapper.deleteById(id);
    }

    public void heartbeat(Long cameraId) {
        cameraMapper.update(null, Wrappers.<Camera>lambdaUpdate()
                .eq(Camera::getId, cameraId)
                .set(Camera::getLastHeartbeat, LocalDateTime.now()));
    }

    private void validate(Camera camera) {
        if (!StringUtils.hasText(camera.getCameraName())) {
            throw new BizException("摄像头名称不能为空");
        }
        String type = camera.getStreamType();
        if (StringUtils.hasText(type) && !"DEMO".equals(type) && !StringUtils.hasText(camera.getStreamUrl())) {
            throw new BizException("非 DEMO 类型必须填写视频源地址");
        }
    }

    public CameraMapper mapper() {
        return cameraMapper;
    }

    public List<Camera> enabledCameras() {
        return cameraMapper.selectList(Wrappers.<Camera>lambdaQuery().eq(Camera::getEnabled, 1));
    }

    public Function<Long, String> nameResolver() {
        return id -> {
            Camera camera = cameraMapper.selectById(id);
            return camera == null ? null : camera.getCameraName();
        };
    }
}
