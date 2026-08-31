package cn.iocoder.yudao.framework.xss.config;

import cn.iocoder.yudao.framework.xss.core.clean.XssCleaner;
import cn.iocoder.yudao.framework.xss.core.filter.XssFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import static org.assertj.core.api.Assertions.assertThat;

class YudaoXssAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(YudaoXssAutoConfiguration.class))
            .withUserConfiguration(PathMatcherConfiguration.class);

    @Test
    void shouldKeepCleanerAvailableWhenGlobalXssIsDisabled() {
        contextRunner.withPropertyValues("yudao.xss.enable=false").run(context -> {
            assertThat(context).hasSingleBean(XssCleaner.class);
            assertThat(context).doesNotHaveBean(JsonMapperBuilderCustomizer.class);
            assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
        });
    }

    @Test
    void shouldRegisterGlobalXssComponentsWhenEnabled() {
        contextRunner.withPropertyValues("yudao.xss.enable=true").run(context -> {
            assertThat(context).hasSingleBean(XssCleaner.class);
            assertThat(context).hasSingleBean(JsonMapperBuilderCustomizer.class);
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
            assertThat(context.getBean(FilterRegistrationBean.class).getFilter()).isInstanceOf(XssFilter.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class PathMatcherConfiguration {

        @Bean
        PathMatcher pathMatcher() {
            return new AntPathMatcher();
        }
    }

}
