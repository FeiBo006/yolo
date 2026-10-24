package com.yuanyu.safety.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yuanyu.safety.common.BizException;
import com.yuanyu.safety.common.PrivacyUtil;
import com.yuanyu.safety.entity.Site;
import com.yuanyu.safety.mapper.CameraMapper;
import com.yuanyu.safety.mapper.SiteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SiteService {

    private final SiteMapper siteMapper;
    private final CameraMapper cameraMapper;

    @Value("${app.privacy.mask-site-location:true}")
    private boolean maskSiteLocation;

    /**
     * 列表属于浏览态，默认只返回脱敏后的地址。
     */
    public List<Site> list(String keyword) {
        List<Site> sites = siteMapper.selectList(Wrappers.<Site>lambdaQuery()
                .like(StringUtils.hasText(keyword), Site::getSiteName, keyword)
                .orderByDesc(Site::getId));
        if (maskSiteLocation) {
            sites.forEach(site -> site.setAddress(PrivacyUtil.maskAddress(site.getAddress())));
        }
        return sites;
    }

    /**
     * 编辑态读取完整地址，仅 ADMIN / MANAGER 可调用。
     */
    public Site detail(Long id) {
        Site site = siteMapper.selectById(id);
        if (site == null) {
            throw new BizException(404, "工地不存在");
        }
        return site;
    }

    public Site create(Site site) {
        if (!StringUtils.hasText(site.getSiteName())) {
            throw new BizException("工地名称不能为空");
        }
        if (PrivacyUtil.isMasked(site.getAddress())) {
            throw new BizException("地址不能包含脱敏占位符");
        }
        site.setId(null);
        site.setStatus(site.getStatus() == null ? 1 : site.getStatus());
        siteMapper.insert(site);
        return site;
    }

    public Site update(Long id, Site input) {
        Site site = siteMapper.selectById(id);
        if (site == null) {
            throw new BizException(404, "工地不存在");
        }
        site.setSiteName(input.getSiteName());
        // 前端若误把脱敏地址回传，保留数据库中的完整地址，避免覆盖
        if (!PrivacyUtil.isMasked(input.getAddress())) {
            site.setAddress(input.getAddress());
        }
        site.setManager(input.getManager());
        site.setPhone(input.getPhone());
        if (input.getStatus() != null) {
            site.setStatus(input.getStatus());
        }
        siteMapper.updateById(site);
        return site;
    }

    public void delete(Long id) {
        Long cameras = cameraMapper.selectCount(Wrappers.<com.yuanyu.safety.entity.Camera>lambdaQuery()
                .eq(com.yuanyu.safety.entity.Camera::getSiteId, id));
        if (cameras != null && cameras > 0) {
            throw new BizException("该工地下还有摄像头，无法删除");
        }
        siteMapper.deleteById(id);
    }
}
