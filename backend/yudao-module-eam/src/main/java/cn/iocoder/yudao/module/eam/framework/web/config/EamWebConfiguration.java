package cn.iocoder.yudao.module.eam.framework.web.config;

import cn.iocoder.yudao.framework.swagger.config.YudaoSwaggerAutoConfiguration;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * eam 模块的 web 组件的 Configuration
 */
@Configuration(proxyBeanMethods = false)
public class EamWebConfiguration {

    /**
     * eam 模块的 API 分组
     */
    @Bean
    public GroupedOpenApi eamGroupedOpenApi() {
        return YudaoSwaggerAutoConfiguration.buildGroupedOpenApi("eam");
    }

}
