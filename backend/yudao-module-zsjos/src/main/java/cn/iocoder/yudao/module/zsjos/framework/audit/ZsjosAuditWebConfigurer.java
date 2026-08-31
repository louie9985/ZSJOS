package cn.iocoder.yudao.module.zsjos.framework.audit;

import cn.iocoder.yudao.module.zsjos.service.audit.BusinessAuditService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class ZsjosAuditWebConfigurer implements WebMvcConfigurer {

    private final BusinessAuditService auditService;

    public ZsjosAuditWebConfigurer(BusinessAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ZsjosAuditInterceptor(auditService));
    }
}
