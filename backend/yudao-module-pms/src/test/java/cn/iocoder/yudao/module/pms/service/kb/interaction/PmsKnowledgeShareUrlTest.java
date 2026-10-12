package cn.iocoder.yudao.module.pms.service.kb.interaction;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.KNOWLEDGE_DOCUMENT_SHARE_CONFIG_INVALID;

class PmsKnowledgeShareUrlTest {
    @Test void configuredOriginAndPathAreUsedForExistingTokens() {
        var urls = new PmsKnowledgeShareUrl();
        ReflectionTestUtils.setField(urls, "publicBaseUrl", " https://public.example.com/education/// ");
        assertEquals("https://public.example.com/education/public-api/pms/kb/document-share/page?token=legacy-token",
                urls.build("legacy-token"));
        assertTrue(urls.build("a&b").endsWith("token=a%26b"));
    }

    @Test void invalidDeploymentConfigurationCannotBecomeAPublicLink() {
        var urls = new PmsKnowledgeShareUrl();
        for (String invalid : new String[]{"", "/relative", "javascript:alert(1)", "https://user:pass@example.com",
                "https://example.com?redirect=1", "https://example.com#fragment", "https://"}) {
            ReflectionTestUtils.setField(urls, "publicBaseUrl", invalid);
            assertEquals(KNOWLEDGE_DOCUMENT_SHARE_CONFIG_INVALID.getCode(),
                    assertThrows(ServiceException.class, () -> urls.build("token")).getCode());
        }
    }
}
