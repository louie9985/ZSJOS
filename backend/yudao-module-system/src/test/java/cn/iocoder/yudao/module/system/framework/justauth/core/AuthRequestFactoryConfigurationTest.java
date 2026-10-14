package cn.iocoder.yudao.module.system.framework.justauth.core;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import com.xkcoding.justauth.autoconfigure.ExtendProperties;
import com.xkcoding.justauth.autoconfigure.JustAuthProperties;
import me.zhyd.oauth.cache.AuthStateCache;
import me.zhyd.oauth.config.AuthConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AuthRequestFactoryConfigurationTest {
    private final JustAuthProperties properties = new JustAuthProperties();
    private final AuthRequestFactory factory = new AuthRequestFactory(properties, mock(AuthStateCache.class));

    @Test void missingKnownSourceConfigurationIsActionable() {
        properties.setType(new HashMap<>());
        properties.setExtend(null);
        ServiceException error = assertThrows(ServiceException.class, () -> factory.get("GITHUB"));
        assertEquals(1_002_018_003, error.getCode());
    }

    @Test void unsupportedSourceIsDistinctEvenWithoutExtensionConfiguration() {
        properties.setExtend(null);
        ServiceException error = assertThrows(ServiceException.class, () -> factory.get("NOT_A_PROVIDER"));
        assertEquals(1_002_018_002, error.getCode());
    }

    @Test void blankSourceAndIncompleteExtensionDoNotThrowNullPointerException() {
        properties.setExtend(new ExtendProperties());
        assertEquals(1_002_018_002, assertThrows(ServiceException.class, () -> factory.get(" ")).getCode());
        assertEquals(1_002_018_002,
                assertThrows(ServiceException.class, () -> factory.get("NOT_A_PROVIDER")).getCode());
        assertDoesNotThrow(factory::oauthList);
    }

    @Test void tenantConfigurationWorksWithoutAnEmptyGlobalPlaceholder() {
        properties.setType(new HashMap<>());
        assertNotNull(factory.get("GITHUB", config -> {
            config.setClientId("fixture-client");
            config.setClientSecret("fixture-secret");
            config.setRedirectUri("https://example.test/callback");
        }));
        assertTrue(properties.getType().isEmpty());
    }

    @Test void incompleteKnownConfigurationIsNotAnUnsupportedProvider() {
        properties.getType().put("GITHUB", new AuthConfig());
        assertEquals(1_002_018_003,
                assertThrows(ServiceException.class, () -> factory.get("GITHUB")).getCode());
    }
}
