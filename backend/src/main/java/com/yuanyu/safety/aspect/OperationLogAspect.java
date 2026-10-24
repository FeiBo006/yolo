package com.yuanyu.safety.aspect;

import com.yuanyu.safety.entity.OperationLog;
import com.yuanyu.safety.mapper.OperationLogMapper;
import com.yuanyu.safety.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 记录非 GET 的业务操作。刻意不记录请求参数，避免密码等敏感信息落库。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogMapper operationLogMapper;

    @Around("execution(public * com.yuanyu.safety.controller..*(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long started = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        try {
            saveLog(joinPoint, System.currentTimeMillis() - started);
        } catch (Exception e) {
            log.debug("操作日志写入失败：{}", e.getMessage());
        }
        return result;
    }

    private void saveLog(ProceedingJoinPoint joinPoint, long cost) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        if ("GET".equalsIgnoreCase(request.getMethod()) || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return;
        }
        String controller = joinPoint.getTarget().getClass().getSimpleName();
        if ("InternalController".equals(controller) || "AuthController".equals(controller)) {
            return;
        }
        OperationLog entity = new OperationLog();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser user) {
            entity.setUserId(user.id());
            entity.setUsername(user.username());
        }
        entity.setModule(controller.replace("Controller", ""));
        entity.setOperation(joinPoint.getSignature().getName());
        entity.setMethod(request.getMethod() + " " + request.getRequestURI());
        entity.setIp(request.getRemoteAddr());
        entity.setCostMs(cost);
        operationLogMapper.insert(entity);
    }
}
