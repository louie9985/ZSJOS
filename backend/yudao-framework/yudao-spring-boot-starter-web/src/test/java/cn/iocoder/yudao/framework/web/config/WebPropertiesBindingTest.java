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
import static org.springframework.boot.context.properties.source.ConfigurationPropertySources.from;

class WebPropertiesBindingTest {

    @Test
    void bindsIndependentPartnerApiFromDefaults() throws Exception {
        String yaml = """
                yudao:
                  web:
                    partner-api:
                      prefix: /part-api
                      controller: "**.controller.app.partner.**"
                """;
        MutablePropertySources propertySources = new MutablePropertySources();
        new YamlPropertySourceLoader().load("test", new ByteArrayResource(
                yaml.getBytes(StandardCharsets.UTF_8))).forEach(propertySources::addLast);

        WebProperties properties = new Binder(from(propertySources))
                .bind("yudao.web", Bindable.of(WebProperties.class))
                .orElseThrow(() -> new IllegalStateException("Web properties were not bound"));

        assertEquals("/part-api", properties.getPartnerApi().getPrefix());
        assertEquals("**.controller.app.partner.**", properties.getPartnerApi().getController());

        new WebFrameworkUtils(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/part-api/zsjos/partner/me");
        assertEquals(UserTypeEnum.PARTNER.getValue(), WebFrameworkUtils.getLoginUserType(request));
    }

}
