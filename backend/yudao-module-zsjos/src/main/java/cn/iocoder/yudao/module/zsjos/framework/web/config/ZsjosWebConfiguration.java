package cn.iocoder.yudao.module.zsjos.framework.web.config;

import cn.iocoder.yudao.framework.common.enums.WebFilterOrderEnum;
import cn.iocoder.yudao.framework.swagger.config.YudaoSwaggerAutoConfiguration;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.zsjos.framework.forcedform.ForcedFormRequestFilter;
import cn.iocoder.yudao.module.zsjos.service.forcedform.ForcedFormService;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * zsjos 模块的 Web 组件配置。
 */
@Configuration(proxyBeanMethods = false)
public class ZsjosWebConfiguration {

    /**
     * zsjos 模块的 API 分组。
     */
    @Bean
    public GroupedOpenApi zsjosGroupedOpenApi() {
        return YudaoSwaggerAutoConfiguration.buildGroupedOpenApi("zsjos");
    }

    @Bean
    public FilterRegistrationBean<ForcedFormRequestFilter> forcedFormRequestFilter(WebProperties webProperties,
                                                                                   ForcedFormService forcedFormService) {
        FilterRegistrationBean<ForcedFormRequestFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new ForcedFormRequestFilter(webProperties, forcedFormService));
        registrationBean.setOrder(WebFilterOrderEnum.FORCED_FORM_FILTER);
        return registrationBean;
    }

}
