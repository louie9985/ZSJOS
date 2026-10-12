package cn.iocoder.yudao.module.pms.service.kb.interaction;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.KNOWLEDGE_DOCUMENT_SHARE_CONFIG_INVALID;

/** Public addresses are deployment configuration, never the management browser's origin. */
@Component
public class PmsKnowledgeShareUrl {
    @Value("${pms.knowledge-share.public-base-url:${system.notice-share.public-base-url:${ZSJOS_PUBLIC_H5_BASE_URL:}}}")
    private String publicBaseUrl;

    public String requireBaseUrl() {
        String value = publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/+$", "");
        try {
            URI uri = URI.create(value);
            if (!safeUrl(value) || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw exception(KNOWLEDGE_DOCUMENT_SHARE_CONFIG_INVALID);
            }
        } catch (IllegalArgumentException ex) {
            throw exception(KNOWLEDGE_DOCUMENT_SHARE_CONFIG_INVALID);
        }
        return value;
    }

    public String build(String token) {
        return requireBaseUrl() + "/public-api/pms/kb/document-share/page?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    public static boolean safeUrl(String value) {
        if (value == null) return false;
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
