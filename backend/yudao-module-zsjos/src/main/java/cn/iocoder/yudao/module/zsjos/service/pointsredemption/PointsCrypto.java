package cn.iocoder.yudao.module.zsjos.service.pointsredemption;
import cn.hutool.crypto.SecureUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
@Component
public class PointsCrypto {
    @Value("${mybatis-plus.encryptor.password:}") private String key;
    private byte[] keyBytes() {
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        if (bytes.length != 16 && bytes.length != 24 && bytes.length != 32) throw PointsErrors.unavailable();
        return bytes;
    }
    public void validate() { keyBytes(); }
    public String encrypt(String value) { return SecureUtil.aes(keyBytes()).encryptBase64(value); }
    public String decrypt(String value) { return SecureUtil.aes(keyBytes()).decryptStr(value, StandardCharsets.UTF_8); }
}
