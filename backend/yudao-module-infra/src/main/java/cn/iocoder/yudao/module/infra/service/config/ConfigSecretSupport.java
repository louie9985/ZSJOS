package cn.iocoder.yudao.module.infra.service.config;

import cn.hutool.crypto.SecureUtil;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.infra.dal.dataobject.config.ConfigDO;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.CONFIG_VALUE_INVALID;

/** Uses the existing framework encryption key; browser-facing reads never decrypt. */
@Component
public class ConfigSecretSupport {
    @Value("${mybatis-plus.encryptor.password:}") private String encryptionKey;
    @Resource private SecurityFrameworkService security;

    public void prepare(ConfigDO next, ConfigDO previous) {
        boolean wasSecret = previous != null && Boolean.TRUE.equals(previous.getSensitive());
        boolean secret = wasSecret || Boolean.TRUE.equals(next.getSensitive())
                || "zsjos.points-redemption.api-key".equals(next.getConfigKey());
        next.setSensitive(secret);
        if (secret) {
            if (!security.hasPermission("infra:config:secret-update"))
                throw new org.springframework.security.access.AccessDeniedException("无敏感配置修改权限");
            if (previous != null && !Objects.equals(previous.getConfigKey(), next.getConfigKey()))
                throw exception(CONFIG_VALUE_INVALID);
            next.setVisible(false);
            String raw = next.getValue();
            if (raw == null || raw.isBlank()) {
                if (previous == null || !wasSecret && previous.getValue() != null && !previous.getValue().isBlank())
                    throw exception(CONFIG_VALUE_INVALID);
                // Leave the value null so MyBatis keeps the current ciphertext. Copying a
                // previously read ciphertext would allow a concurrent rotation to be lost.
                next.setValue(null);
            }
            else {
                if (raw.contains("****") || raw.length() < 32
                        && "zsjos.points-redemption.api-key".equals(next.getConfigKey())) throw exception(CONFIG_VALUE_INVALID);
                String encrypted = SecureUtil.aes(key()).encryptBase64(raw);
                if (encrypted.length() > 500) throw exception(CONFIG_VALUE_INVALID);
                next.setValue(encrypted);
            }
        } else if (next.getValue() == null || next.getValue().isBlank()) throw exception(CONFIG_VALUE_INVALID);
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) return null;
        return SecureUtil.aes(key()).decryptStr(ciphertext, StandardCharsets.UTF_8);
    }

    private byte[] key() {
        byte[] bytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        if (bytes.length != 16 && bytes.length != 24 && bytes.length != 32)
            throw exception(CONFIG_VALUE_INVALID);
        return bytes;
    }
}
