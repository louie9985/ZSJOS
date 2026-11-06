package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;

@Component
public class ExamCollectionTokens {
    private static final SecureRandom RANDOM = new SecureRandom();
    @Value("${mybatis-plus.encryptor.password:}") private String key;
    public String create() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public String hash(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw exception(SESSION_INVALID);
        return DigestUtil.sha256Hex(token);
    }
    public String identityHash(String identity) {
        if (key == null || key.isBlank()) throw exception(KEY_MISSING);
        var normalized = ExamCollectionPolicy.normalizeIdentity(identity);
        if (normalized.isEmpty()) return null;
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("exam-identity:v1:" + normalized).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException error) { throw exception(KEY_MISSING); }
    }
}
