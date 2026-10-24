package com.yuanyu.safety.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuanyu.safety.common.BizException;
import com.yuanyu.safety.common.PageResult;
import com.yuanyu.safety.dto.AlarmEvent;
import com.yuanyu.safety.dto.AlarmHandleRequest;
import com.yuanyu.safety.entity.Alarm;
import com.yuanyu.safety.entity.Camera;
import com.yuanyu.safety.mapper.AlarmMapper;
import com.yuanyu.safety.mapper.CameraMapper;
import com.yuanyu.safety.security.LoginUser;
import com.yuanyu.safety.ws.AlarmWebSocketHandler;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmService {

    public static final Map<String, String> BEHAVIOR_LABELS = Map.of(
            "NO_HELMET", "未佩戴安全帽",
            "NO_VEST", "未穿反光衣",
            "INTRUSION", "危险区域入侵",
            "SMOKING", "吸烟",
            "PHONE", "打电话",
            "FALL", "人员跌倒"
    );

    public static final Map<String, String> STATUS_LABELS = Map.of(
            "PENDING", "待处理",
            "CONFIRMED", "已确认",
            "REPAIRED", "已整改",
            "FALSE_ALARM", "误报"
    );

    public static final Map<String, String> LEVEL_LABELS = Map.of(
            "LOW", "低",
            "MEDIUM", "中",
            "HIGH", "高"
    );

    private static final Set<String> ALLOWED_STATUS = Set.of("CONFIRMED", "REPAIRED", "FALSE_ALARM");

    private final AlarmMapper alarmMapper;
    private final CameraMapper cameraMapper;
    private final AlarmWebSocketHandler webSocketHandler;

    public PageResult<Alarm> page(String status, String behaviorType, Long cameraId, String keyword,
                                  LocalDateTime start, LocalDateTime end, long page, long size) {
        Page<Alarm> result = alarmMapper.selectPage(new Page<>(page, size),
                buildQuery(status, behaviorType, cameraId, keyword, start, end)
                        .orderByDesc(Alarm::getAlarmTime));
        return new PageResult<>(result.getRecords(), result.getTotal(), page, size);
    }

    public Alarm get(Long id) {
        Alarm alarm = alarmMapper.selectById(id);
        if (alarm == null) {
            throw new BizException(404, "告警记录不存在");
        }
        return alarm;
    }

    @Transactional
    public Alarm handle(Long id, AlarmHandleRequest request, LoginUser user) {
        if (!ALLOWED_STATUS.contains(request.getStatus())) {
            throw new BizException("不支持的处置状态：" + request.getStatus());
        }
        Alarm alarm = alarmMapper.selectById(id);
        if (alarm == null) {
            throw new BizException(404, "告警记录不存在");
        }
        alarm.setStatus(request.getStatus());
        alarm.setHandlerId(user.id());
        alarm.setHandlerName(user.realName() == null ? user.username() : user.realName());
        alarm.setHandleTime(LocalDateTime.now());
        alarm.setHandleRemark(request.getRemark());
        alarmMapper.updateById(alarm);
        webSocketHandler.broadcast(Map.of("type", "ALARM_UPDATE", "data", alarm));
        return alarm;
    }

    /**
     * 幂等接入算法事件，重复 alarmNo 直接忽略。
     */
    @Transactional
    public Alarm ingest(AlarmEvent event) {
        if (!StringUtils.hasText(event.getAlarmNo())) {
            throw new BizException("alarmNo 不能为空");
        }
        Long exists = alarmMapper.selectCount(Wrappers.<Alarm>lambdaQuery()
                .eq(Alarm::getAlarmNo, event.getAlarmNo()));
        if (exists != null && exists > 0) {
            return null;
        }
        Camera camera = event.getCameraId() == null ? null : cameraMapper.selectById(event.getCameraId());
        Alarm alarm = new Alarm();
        alarm.setAlarmNo(event.getAlarmNo());
        alarm.setCameraId(event.getCameraId());
        alarm.setCameraName(camera == null ? "未知摄像头" : camera.getCameraName());
        alarm.setSiteId(camera == null ? null : camera.getSiteId());
        alarm.setBehaviorType(event.getBehaviorType());
        alarm.setLevel(StringUtils.hasText(event.getLevel()) ? event.getLevel() : "MEDIUM");
        alarm.setConfidence(event.getConfidence() == null ? new BigDecimal("0.500") : event.getConfidence());
        alarm.setTrackId(event.getTrackId());
        alarm.setBbox(event.getBbox());
        alarm.setSnapshotUrl(event.getSnapshotUrl());
        alarm.setAlarmTime(event.getAlarmTime() == null ? LocalDateTime.now() : event.getAlarmTime());
        alarm.setStatus("PENDING");
        alarmMapper.insert(alarm);
        log.info("收到告警：{} {} conf={}", alarm.getCameraName(), alarm.getBehaviorType(), alarm.getConfidence());
        webSocketHandler.broadcast(Map.of("type", "ALARM", "data", alarm));
        return alarm;
    }

    public void export(String status, String behaviorType, Long cameraId, String keyword,
                       LocalDateTime start, LocalDateTime end, HttpServletResponse response) {
        List<Alarm> alarms = alarmMapper.selectList(buildQuery(status, behaviorType, cameraId, keyword, start, end)
                .orderByDesc(Alarm::getAlarmTime).last("limit 5000"));
        String[] headers = {"告警编号", "摄像头", "行为类型", "告警级别", "置信度", "告警时间", "状态", "处置人", "处置时间", "处置备注"};
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("告警记录");
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            int rowIndex = 1;
            for (Alarm alarm : alarms) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(alarm.getAlarmNo());
                row.createCell(1).setCellValue(alarm.getCameraName());
                row.createCell(2).setCellValue(BEHAVIOR_LABELS.getOrDefault(alarm.getBehaviorType(), alarm.getBehaviorType()));
                row.createCell(3).setCellValue(LEVEL_LABELS.getOrDefault(alarm.getLevel(), alarm.getLevel()));
                row.createCell(4).setCellValue(alarm.getConfidence() == null ? 0 : alarm.getConfidence().doubleValue());
                row.createCell(5).setCellValue(alarm.getAlarmTime() == null ? "" : alarm.getAlarmTime().toString());
                row.createCell(6).setCellValue(STATUS_LABELS.getOrDefault(alarm.getStatus(), alarm.getStatus()));
                row.createCell(7).setCellValue(alarm.getHandlerName() == null ? "" : alarm.getHandlerName());
                row.createCell(8).setCellValue(alarm.getHandleTime() == null ? "" : alarm.getHandleTime().toString());
                row.createCell(9).setCellValue(alarm.getHandleRemark() == null ? "" : alarm.getHandleRemark());
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, i == 9 ? 12000 : 5000);
            }
            String filename = URLEncoder.encode("告警记录.xlsx", StandardCharsets.UTF_8);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
            workbook.write(response.getOutputStream());
        } catch (IOException e) {
            throw new BizException("导出失败：" + e.getMessage());
        }
    }

    private LambdaQueryWrapper<Alarm> buildQuery(String status, String behaviorType, Long cameraId,
                                                 String keyword, LocalDateTime start, LocalDateTime end) {
        return Wrappers.<Alarm>lambdaQuery()
                .eq(StringUtils.hasText(status), Alarm::getStatus, status)
                .eq(StringUtils.hasText(behaviorType), Alarm::getBehaviorType, behaviorType)
                .eq(cameraId != null, Alarm::getCameraId, cameraId)
                .ge(start != null, Alarm::getAlarmTime, start)
                .le(end != null, Alarm::getAlarmTime, end)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Alarm::getCameraName, keyword).or()
                        .like(Alarm::getAlarmNo, keyword));
    }
}
