package cn.iocoder.yudao.module.zsjos.framework.permission;

import cn.iocoder.yudao.framework.common.util.spring.SpringExpressionUtils;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadObjectPermissionService;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderObjectPermissionService;
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
    @Resource
    private SalesOrderObjectPermissionService salesOrderObjectPermissionService;

    @Before("@annotation(permission)")
    public void check(JoinPoint joinPoint, ZsjosPermission permission) {
        Map<String, Object> values = SpringExpressionUtils.parseExpressions(joinPoint,
                List.of(permission.bizId()));
        Object value = values.get(permission.bizId());
        if (value == null) {
            throw new IllegalArgumentException("Unsupported ZSJOS permission target");
        }
        Long bizId = Long.valueOf(value.toString());
        switch (permission.bizType()) {
            case "lead" -> leadObjectPermissionService.check(bizId, permission.action());
            case "sales-order" -> salesOrderObjectPermissionService.check(bizId, permission.action());
            default -> throw new IllegalArgumentException("Unsupported ZSJOS permission target");
        }
    }
}
