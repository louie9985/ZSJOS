package cn.iocoder.yudao.framework.web.config;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.boot.context.properties.source.ConfigurationPropertySources.from;

class WebPropertiesBindingTest {

    @Test
    void bindsSlashDelimitedAppApiUserTypePrefixFromYaml() throws Exception {
        String yaml = """
                yudao:
                  web:
                    app-api-user-type-prefixes:
                      "[/zsjos/]": 3
                """;
        MutablePropertySources propertySources = new MutablePropertySources();
        new YamlPropertySourceLoader().load("test", new ByteArrayResource(
                yaml.getBytes(StandardCharsets.UTF_8))).forEach(propertySources::addLast);

        WebProperties properties = new Binder(from(propertySources))
                .bind("yudao.web", Bindable.of(WebProperties.class))
                .orElseThrow(() -> new IllegalStateException("Web properties were not bound"));

        assertEquals(Map.of("/zsjos/", UserTypeEnum.PARTNER.getValue()),
                properties.getAppApiUserTypePrefixes());
        assertTrue(properties.isAppApiUserTypePrefixesValid());

        new WebFrameworkUtils(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/app-api/zsjos/partner/me");
        assertEquals(UserTypeEnum.PARTNER.getValue(), WebFrameworkUtils.getLoginUserType(request));
    }

}
