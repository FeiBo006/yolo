package com.yuanyu.safety.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yuanyu.safety.common.BizException;
import com.yuanyu.safety.entity.Area;
import com.yuanyu.safety.entity.Camera;
import com.yuanyu.safety.mapper.AreaMapper;
import com.yuanyu.safety.mapper.CameraMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AreaService {

    private final AreaMapper areaMapper;
    private final CameraMapper cameraMapper;

    public List<Area> list(Long cameraId) {
        List<Area> areas = areaMapper.selectList(Wrappers.<Area>lambdaQuery()
                .eq(cameraId != null, Area::getCameraId, cameraId)
                .orderByDesc(Area::getId));
        areas.forEach(a -> {
            Camera camera = cameraMapper.selectById(a.getCameraId());
            a.setCameraName(camera == null ? null : camera.getCameraName());
        });
        return areas;
    }

    public Area create(Area area) {
        validate(area);
        area.setId(null);
        area.setEnabled(area.getEnabled() == null ? 1 : area.getEnabled());
        area.setAreaType(StringUtils.hasText(area.getAreaType()) ? area.getAreaType() : "DANGER");
        areaMapper.insert(area);
        return area;
    }

    public Area update(Long id, Area input) {
        Area area = areaMapper.selectById(id);
        if (area == null) {
            throw new BizException(404, "区域不存在");
        }
        validate(input);
        area.setCameraId(input.getCameraId());
        area.setAreaName(input.getAreaName());
        area.setAreaType(input.getAreaType());
        area.setPoints(input.getPoints());
        if (input.getEnabled() != null) {
            area.setEnabled(input.getEnabled());
        }
        areaMapper.updateById(area);
        return area;
    }

    public void delete(Long id) {
        areaMapper.deleteById(id);
    }

    private void validate(Area area) {
        if (area.getCameraId() == null) {
            throw new BizException("请选择摄像头");
        }
        if (!StringUtils.hasText(area.getAreaName())) {
            throw new BizException("区域名称不能为空");
        }
        if (!StringUtils.hasText(area.getPoints()) || !area.getPoints().trim().startsWith("[")) {
            throw new BizException("区域坐标格式不正确");
        }
    }
}
