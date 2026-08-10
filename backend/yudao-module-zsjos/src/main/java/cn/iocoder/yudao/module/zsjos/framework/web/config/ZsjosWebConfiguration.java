package cn.iocoder.yudao.module.zsjos.framework.web.config;

import cn.iocoder.yudao.framework.swagger.config.YudaoSwaggerAutoConfiguration;
import org.springdoc.core.models.GroupedOpenApi;
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

}
