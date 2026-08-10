package cn.iocoder.yudao.module.zsjos.framework.permission;

import cn.iocoder.yudao.framework.common.util.spring.SpringExpressionUtils;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadObjectPermissionService;
import jakarta.annotation.Resource;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Aspect
@Component
public class ZsjosPermissionAspect {

    @Resource
    private LeadObjectPermissionService leadObjectPermissionService;

    @Before("@annotation(permission)")
    public void check(JoinPoint joinPoint, ZsjosPermission permission) {
        Map<String, Object> values = SpringExpressionUtils.parseExpressions(joinPoint,
                List.of(permission.bizId()));
        Object value = values.get(permission.bizId());
        if (!"lead".equals(permission.bizType()) || value == null) {
            throw new IllegalArgumentException("Unsupported ZSJOS permission target");
        }
        leadObjectPermissionService.check(Long.valueOf(value.toString()), permission.action());
    }
}
