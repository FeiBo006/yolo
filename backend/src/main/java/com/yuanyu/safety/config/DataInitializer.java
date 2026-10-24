package com.yuanyu.safety.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yuanyu.safety.entity.*;
import com.yuanyu.safety.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

/**
 * 初始化角色、账号与演示数据。
 * 仅在对应表为空时写入，重复启动不会产生脏数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleMapper roleMapper;
    private final UserMapper userMapper;
    private final SiteMapper siteMapper;
    private final CameraMapper cameraMapper;
    private final AreaMapper areaMapper;
    private final RuleMapper ruleMapper;
    private final AlarmMapper alarmMapper;
    private final DetectionRecordMapper detectionRecordMapper;
    private final ModelVersionMapper modelVersionMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedRoles();
        seedUsers();
        seedBusiness();
    }

    private void seedRoles() {
        List<String[]> roles = List.of(
                new String[]{"ADMIN", "系统管理员", "拥有全部权限"},
                new String[]{"MANAGER", "项目经理", "工地、摄像头、区域与告警管理"},
                new String[]{"INSPECTOR", "安全员", "查看监控、处置告警"}
        );
        for (String[] role : roles) {
            Long count = roleMapper.selectCount(Wrappers.<Role>lambdaQuery()
                    .eq(Role::getRoleCode, role[0]));
            if (count == null || count == 0) {
                Role entity = new Role();
                entity.setRoleCode(role[0]);
                entity.setRoleName(role[1]);
                entity.setDescription(role[2]);
                roleMapper.insert(entity);
            }
        }
    }

    private void seedUsers() {
        if (userMapper.selectCount(null) > 0) {
            return;
        }
        createUser("admin", "123456", "系统管理员", "13800000001", "ADMIN");
        createUser("manager", "123456", "张伟", "13800000002", "MANAGER");
        createUser("inspector", "123456", "李强", "13800000003", "INSPECTOR");
        log.info("演示账号已创建：admin / manager / inspector，密码均为 123456");
    }

    private void createUser(String username, String password, String realName, String phone, String role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRealName(realName);
        user.setPhone(phone);
        user.setRoleCode(role);
        user.setStatus(1);
        userMapper.insert(user);
    }

    private void seedBusiness() {
        if (siteMapper.selectCount(null) > 0) {
            return;
        }
        Site site1 = new Site();
        site1.setSiteName("示范工地 A · 实训楼项目");
        site1.setAddress("江西省示例市示例区示例路 1 号");
        site1.setManager("张伟");
        site1.setPhone("13800000002");
        site1.setStatus(1);
        siteMapper.insert(site1);

        Site site2 = new Site();
        site2.setSiteName("示范工地 B · 滨江项目");
        site2.setAddress("江西省示例市经开区示例大道 88 号");
        site2.setManager("王敏");
        site2.setPhone("13800000004");
        site2.setStatus(1);
        siteMapper.insert(site2);

        Camera cam1 = createCamera(site1.getId(), "1 号塔吊全景", "demo://tower", "DEMO", "塔吊东侧", 1);
        Camera cam2 = createCamera(site1.getId(), "南门人员通道", "demo://gate", "DEMO", "工地南门", 1);
        createCamera(site2.getId(), "材料堆场", "", "VIDEO", "堆场西北角", 0);
        createCamera(site1.getId(), "AI 实景演示", "videos/demo_site.mp4", "VIDEO", "实景片段（启用 YOLO 后可检测）", 0);

        Area area1 = new Area();
        area1.setCameraId(cam1.getId());
        area1.setAreaName("塔吊吊装危险区");
        area1.setAreaType("DANGER");
        area1.setPoints("[[0.52,0.32],[0.94,0.32],[0.94,0.90],[0.52,0.90]]");
        area1.setEnabled(1);
        areaMapper.insert(area1);

        Area area2 = new Area();
        area2.setCameraId(cam2.getId());
        area2.setAreaName("临边洞口危险区");
        area2.setAreaType("DANGER");
        area2.setPoints("[[0.04,0.52],[0.36,0.52],[0.36,0.94],[0.04,0.94]]");
        area2.setEnabled(1);
        areaMapper.insert(area2);

        createRule("未佩戴安全帽监测", "NO_HELMET", new BigDecimal("0.450"), 3, 300, "HIGH");
        createRule("未穿反光衣监测", "NO_VEST", new BigDecimal("0.450"), 3, 360, "MEDIUM");
        createRule("危险区域入侵监测", "INTRUSION", new BigDecimal("0.400"), 2, 420, "HIGH");

        seedAlarms(cam1, cam2);
        seedDetections(cam1.getId(), cam2.getId());
        seedModels();
        log.info("演示业务数据初始化完成");
    }

    private Camera createCamera(Long siteId, String name, String url, String type, String location, int enabled) {
        Camera camera = new Camera();
        camera.setSiteId(siteId);
        camera.setCameraName(name);
        camera.setStreamUrl(url);
        camera.setStreamType(type);
        camera.setLocation(location);
        camera.setEnabled(enabled);
        camera.setLastHeartbeat(enabled == 1 ? LocalDateTime.now() : null);
        cameraMapper.insert(camera);
        return camera;
    }

    private void createRule(String name, String behavior, BigDecimal confidence, int frames, int cooldown, String level) {
        Rule rule = new Rule();
        rule.setRuleName(name);
        rule.setBehaviorType(behavior);
        rule.setMinConfidence(confidence);
        rule.setConsecutiveFrames(frames);
        rule.setCooldownSeconds(cooldown);
        rule.setLevel(level);
        rule.setEnabled(1);
        ruleMapper.insert(rule);
    }

    private void seedAlarms(Camera cam1, Camera cam2) {
        Random random = new Random(20261024L);
        String[] behaviors = {"NO_HELMET", "NO_HELMET", "NO_VEST", "INTRUSION"};
        String[] statuses = {"PENDING", "PENDING", "CONFIRMED", "REPAIRED", "REPAIRED", "REPAIRED", "FALSE_ALARM"};
        String[] handlers = {"李强", "王敏", "赵磊"};
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 48; i++) {
            Alarm alarm = new Alarm();
            boolean camOne = random.nextBoolean();
            Camera camera = camOne ? cam1 : cam2;
            String behavior = behaviors[random.nextInt(behaviors.length)];
            String status = statuses[random.nextInt(statuses.length)];
            LocalDateTime time = now.minusMinutes(5 + random.nextInt(7 * 24 * 60 - 10))
                    .withSecond(random.nextInt(60));
            alarm.setAlarmNo(String.format("DEMO%06d", i + 1));
            alarm.setCameraId(camera.getId());
            alarm.setCameraName(camera.getCameraName());
            alarm.setSiteId(camera.getSiteId());
            alarm.setBehaviorType(behavior);
            alarm.setLevel("NO_VEST".equals(behavior) ? "MEDIUM" : "HIGH");
            alarm.setConfidence(BigDecimal.valueOf(0.50 + random.nextDouble() * 0.45)
                    .setScale(3, RoundingMode.HALF_UP));
            alarm.setTrackId(10 + random.nextInt(80));
            double x1 = 0.10 + random.nextDouble() * 0.45;
            double y1 = 0.20 + random.nextDouble() * 0.30;
            double bw = 0.06 + random.nextDouble() * 0.10;
            double bh = 0.12 + random.nextDouble() * 0.18;
            alarm.setBbox(String.format("%.3f,%.3f,%.3f,%.3f", x1, y1, Math.min(0.98, x1 + bw), Math.min(0.98, y1 + bh)));
            alarm.setSnapshotUrl(null);
            alarm.setAlarmTime(time);
            alarm.setStatus(status);
            if (!"PENDING".equals(status)) {
                alarm.setHandlerId(2L + random.nextInt(3));
                alarm.setHandlerName(handlers[random.nextInt(handlers.length)]);
                LocalDateTime handleTime = time.plusMinutes(3 + random.nextInt(90));
                alarm.setHandleTime(handleTime.isAfter(now) ? now : handleTime);
                alarm.setHandleRemark("PENDING".equals(status) ? null : "现场已核查并完成处置");
            }
            alarmMapper.insert(alarm);
        }
    }

    private void seedDetections(Long cam1, Long cam2) {
        Random random = new Random(7L);
        LocalDateTime now = LocalDateTime.now();
        for (int h = 0; h < 24; h++) {
            insertDetection(cam1, now.minusHours(h), random);
            if (h % 3 == 0) {
                insertDetection(cam2, now.minusHours(h), random);
            }
        }
    }

    private void insertDetection(Long cameraId, LocalDateTime time, Random random) {
        DetectionRecord record = new DetectionRecord();
        int person = 2 + random.nextInt(6);
        int noHelmet = random.nextInt(3);
        record.setCameraId(cameraId);
        record.setRecordTime(time);
        record.setPersonCount(person);
        record.setHelmetCount(person - noHelmet);
        record.setNoHelmetCount(noHelmet);
        record.setVestCount(person - random.nextInt(2));
        record.setNoVestCount(random.nextInt(2));
        record.setFps(BigDecimal.valueOf(18 + random.nextDouble() * 6).setScale(2, RoundingMode.HALF_UP));
        record.setInferenceMs(BigDecimal.valueOf(35 + random.nextDouble() * 30).setScale(2, RoundingMode.HALF_UP));
        record.setModelVersion("mock-v1");
        detectionRecordMapper.insert(record);
    }

    private void seedModels() {
        ModelVersion mock = new ModelVersion();
        mock.setModelName("演示模式（Mock）");
        mock.setVersion("mock-v1");
        mock.setFilePath("-");
        mock.setStatus("ACTIVE");
        mock.setDeployTime(LocalDateTime.now());
        modelVersionMapper.insert(mock);

        ModelVersion safety = new ModelVersion();
        safety.setModelName("YOLOv8n 安全行为模型");
        safety.setVersion("v1.0.0");
        safety.setFilePath("weights/safety.pt");
        safety.setStatus("INACTIVE");
        modelVersionMapper.insert(safety);
    }
}
