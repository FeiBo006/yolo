package com.yuanyu.safety.mq;

import com.yuanyu.safety.config.RabbitConfig;
import com.yuanyu.safety.dto.AlarmEvent;
import com.yuanyu.safety.service.InternalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("mq")
@RequiredArgsConstructor
public class AlarmMqListener {

    private final InternalService internalService;

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onAlarm(AlarmEvent event) {
        log.info("MQ 收到告警：{} {} conf={}", event.getCameraId(), event.getBehaviorType(), event.getConfidence());
        internalService.reportAlarm(event);
    }
}
