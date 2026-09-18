package cn.iocoder.yudao.module.zsjos.framework.allinpay;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

public class AllinpaySigner {
    private final AllinpayProperties properties;
    private volatile PrivateKey privateKey;
    private volatile PublicKey publicKey;

    public AllinpaySigner(AllinpayProperties properties) {
        this.properties = properties;
    }

    /** 在生成支付链接前检查密钥可用性，不发送支付请求。 */
    public void validateKeys() {
        try {
            Signature signature = Signature.getInstance("SHA1withRSA");
            signature.initSign(loadPrivateKey());
            signature.initVerify(loadPublicKey());
        } catch (Exception ex) {
            // 密钥解析异常可能带原始内容，不向调用方传播异常链。
            throw new IllegalStateException("通联签名密钥配置无效");
        }
    }

    public String sign(Map<String, ?> parameters) {
        try {
            Signature signature = Signature.getInstance("SHA1withRSA");
            signature.initSign(loadPrivateKey());
            signature.update(canonical(parameters).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception ex) {
            throw new IllegalStateException("通联私钥签名失败", ex);
        }
    }

    public boolean verify(Map<String, ?> parameters, String encoded) {
        try {
            Signature signature = Signature.getInstance("SHA1withRSA");
            signature.initVerify(loadPublicKey());
            signature.update(canonical(parameters).getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(encoded));
        } catch (Exception ex) {
            return false;
        }
    }

    public static String canonical(Map<String, ?> parameters) {
        return parameters.entrySet().stream()
                .filter(entry -> !"sign".equals(entry.getKey()))
                .filter(entry -> entry.getValue() != null
                        && !entry.getValue().toString().isEmpty()
                        && !"null".equalsIgnoreCase(entry.getValue().toString()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    private PrivateKey loadPrivateKey() throws Exception {
        if (privateKey != null) return privateKey;
        byte[] der;
        // 优先使用直接传入的密钥内容
        if (properties.getMerchantPrivateKey() != null && !properties.getMerchantPrivateKey().isBlank()) {
            der = decodePemString(properties.getMerchantPrivateKey(), "PRIVATE KEY");
        } else {
            // 否则从文件路径读取
            der = decodePem(Path.of(properties.getMerchantPrivateKeyLocation()), "PRIVATE KEY");
        }
        privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        return privateKey;
    }

    private PublicKey loadPublicKey() throws Exception {
        if (publicKey != null) return publicKey;
        byte[] der;
        // 优先使用直接传入的密钥内容
        if (properties.getPlatformPublicKey() != null && !properties.getPlatformPublicKey().isBlank()) {
            der = decodePemString(properties.getPlatformPublicKey(), "PUBLIC KEY");
        } else {
            // 否则从文件路径读取
            der = decodePem(Path.of(properties.getPlatformPublicKeyLocation()), "PUBLIC KEY");
        }
        publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        return publicKey;
    }

    private static byte[] decodePem(Path path, String label) throws Exception {
        String pem = Files.readString(path, StandardCharsets.US_ASCII)
                .replace("-----BEGIN " + label + "-----", "")
                .replace("-----END " + label + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(pem);
    }

    private static byte[] decodePemString(String pem, String label) {
        String cleanPem = pem
                .replace("-----BEGIN " + label + "-----", "")
                .replace("-----END " + label + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(cleanPem);
    }
}
