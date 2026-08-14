package cn.iocoder.yudao.module.zsjos.framework.impersonation;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class ImpersonationWebConfigurer implements WebMvcConfigurer {
    private final ImpersonationInterceptor interceptor;

    public ImpersonationWebConfigurer(ImpersonationInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/admin-api/zsjos/**", "/zsjos/**")
                .excludePathPatterns("/admin-api/zsjos/impersonation/**", "/zsjos/impersonation/**");
    }
}
