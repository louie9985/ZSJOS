package cn.iocoder.yudao.module.zsjos.framework.permission;

import cn.iocoder.yudao.framework.common.util.spring.SpringExpressionUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Aspect
@Component
public class ZsjosPermissionAspect {

    private final Map<String, ZsjosObjectPermissionProvider> providers = new LinkedHashMap<>();

    public ZsjosPermissionAspect(List<ZsjosObjectPermissionProvider> providerList) {
        for (ZsjosObjectPermissionProvider provider : providerList) {
            if (providers.put(provider.getBizType(), provider) != null) {
                throw new IllegalStateException("Duplicate ZSJOS permission provider: " + provider.getBizType());
            }
        }
    }

    @Before("@annotation(permission)")
    public void check(JoinPoint joinPoint, ZsjosPermission permission) {
        Map<String, Object> values = SpringExpressionUtils.parseExpressions(joinPoint,
                List.of(permission.bizId()));
        Object value = values.get(permission.bizId());
        ZsjosObjectPermissionProvider provider = providers.get(permission.bizType());
        if (provider == null || value == null) {
            throw new IllegalArgumentException("Unsupported ZSJOS permission target");
        }
        Long bizId = Long.valueOf(value.toString());
        provider.check(bizId, permission.action(), getLoginUserId());
    }
}
