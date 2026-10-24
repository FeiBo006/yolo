package com.yuanyu.safety.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yuanyu.safety.entity.Alarm;
import com.yuanyu.safety.entity.Camera;
import com.yuanyu.safety.entity.DetectionRecord;
import com.yuanyu.safety.mapper.AlarmMapper;
import com.yuanyu.safety.mapper.CameraMapper;
import com.yuanyu.safety.mapper.DetectionRecordMapper;
import com.yuanyu.safety.ws.AlarmWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM-dd");

    private final AlarmMapper alarmMapper;
    private final CameraMapper cameraMapper;
    private final DetectionRecordMapper detectionRecordMapper;
    private final AlarmWebSocketHandler webSocketHandler;

    public Map<String, Object> summary() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        long todayAlarms = count(Wrappers.<Alarm>lambdaQuery().ge(Alarm::getAlarmTime, today));
        long pending = count(Wrappers.<Alarm>lambdaQuery().eq(Alarm::getStatus, "PENDING"));
        long todayHandled = count(Wrappers.<Alarm>lambdaQuery()
                .ge(Alarm::getAlarmTime, today).ne(Alarm::getStatus, "PENDING"));
        long todayNoHelmet = count(Wrappers.<Alarm>lambdaQuery()
                .ge(Alarm::getAlarmTime, today).eq(Alarm::getBehaviorType, "NO_HELMET"));
        long total = alarmMapper.selectCount(null);

        List<Camera> cameras = cameraMapper.selectList(null);
        long enabled = cameras.stream().filter(c -> c.getEnabled() != null && c.getEnabled() == 1).count();
        LocalDateTime onlineLine = LocalDateTime.now().minusMinutes(2);
        long online = cameras.stream()
                .filter(c -> c.getLastHeartbeat() != null && c.getLastHeartbeat().isAfter(onlineLine))
                .count();

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("todayAlarm", todayAlarms);
        map.put("pendingAlarm", pending);
        map.put("todayHandled", todayHandled);
        map.put("todayNoHelmet", todayNoHelmet);
        map.put("totalAlarm", total);
        map.put("cameraTotal", enabled);
        map.put("cameraOnline", online);
        map.put("wsOnline", webSocketHandler.onlineCount());
        map.put("handleRate", todayAlarms == 0 ? 0 : Math.round(todayHandled * 1000.0 / todayAlarms) / 10.0);
        return map;
    }

    public List<Map<String, Object>> trend(int days) {
        int safeDays = Math.max(1, Math.min(days, 90));
        LocalDate from = LocalDate.now().minusDays(safeDays - 1L);
        List<Alarm> alarms = alarmMapper.selectList(Wrappers.<Alarm>lambdaQuery()
                .ge(Alarm::getAlarmTime, from.atStartOfDay()));

        Map<LocalDate, long[]> buckets = new TreeMap<>();
        for (int i = 0; i < safeDays; i++) {
            buckets.put(from.plusDays(i), new long[2]);
        }
        for (Alarm alarm : alarms) {
            LocalDate date = alarm.getAlarmTime().toLocalDate();
            long[] bucket = buckets.get(date);
            if (bucket != null) {
                bucket[0]++;
                if (!"PENDING".equals(alarm.getStatus())) {
                    bucket[1]++;
                }
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        buckets.forEach((date, counts) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date.format(DATE));
            item.put("total", counts[0]);
            item.put("handled", counts[1]);
            result.add(item);
        });
        return result;
    }

    public List<Map<String, Object>> ranking(int days) {
        int safeDays = Math.max(1, Math.min(days, 90));
        LocalDateTime from = LocalDate.now().minusDays(safeDays - 1L).atStartOfDay();
        List<Alarm> alarms = alarmMapper.selectList(Wrappers.<Alarm>lambdaQuery()
                .ge(Alarm::getAlarmTime, from));
        Map<String, Long> grouped = new LinkedHashMap<>();
        for (Alarm alarm : alarms) {
            grouped.merge(alarm.getBehaviorType(), 1L, Long::sum);
        }
        return grouped.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("type", e.getKey());
                    item.put("label", AlarmService.BEHAVIOR_LABELS.getOrDefault(e.getKey(), e.getKey()));
                    item.put("count", e.getValue());
                    return item;
                })
                .toList();
    }

    public List<Map<String, Object>> statusDistribution(int days) {
        int safeDays = Math.max(1, Math.min(days, 90));
        LocalDateTime from = LocalDate.now().minusDays(safeDays - 1L).atStartOfDay();
        List<Alarm> alarms = alarmMapper.selectList(Wrappers.<Alarm>lambdaQuery()
                .ge(Alarm::getAlarmTime, from));
        Map<String, Long> grouped = new LinkedHashMap<>();
        for (Alarm alarm : alarms) {
            grouped.merge(alarm.getStatus(), 1L, Long::sum);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        grouped.forEach((status, count) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", status);
            item.put("label", AlarmService.STATUS_LABELS.getOrDefault(status, status));
            item.put("count", count);
            result.add(item);
        });
        return result;
    }

    public DetectionRecord latestDetection(Long cameraId) {
        return detectionRecordMapper.selectOne(Wrappers.<DetectionRecord>lambdaQuery()
                .eq(cameraId != null, DetectionRecord::getCameraId, cameraId)
                .orderByDesc(DetectionRecord::getRecordTime)
                .last("limit 1"));
    }

    public List<DetectionRecord> recentDetections(Long cameraId, int limit) {
        return detectionRecordMapper.selectList(Wrappers.<DetectionRecord>lambdaQuery()
                .eq(cameraId != null, DetectionRecord::getCameraId, cameraId)
                .orderByDesc(DetectionRecord::getRecordTime)
                .last("limit " + Math.max(1, Math.min(limit, 200))));
    }

    private long count(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Alarm> wrapper) {
        Long value = alarmMapper.selectCount(wrapper);
        return value == null ? 0 : value;
    }
}
