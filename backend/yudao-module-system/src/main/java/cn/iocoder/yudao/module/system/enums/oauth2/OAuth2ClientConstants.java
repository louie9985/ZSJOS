package cn.iocoder.yudao.module.system.enums.oauth2;

/**
 * OAuth2.0 客户端的通用枚举
 *
 * @author 芋道源码
 */
public interface OAuth2ClientConstants {

    String CLIENT_ID_DEFAULT = "default";
    String CLIENT_ID_ZSJOS_PC = "zsjos-pc";
    String CLIENT_ID_ZSJOS_MOBILE = "zsjos-mobile";

    String CONFIG_PC_MAX_DEVICES = "zsjos.auth.pc.max-devices";
    String CONFIG_MOBILE_MAX_DEVICES = "zsjos.auth.mobile.max-devices";
    String CONFIG_REMEMBER_DAYS = "zsjos.auth.remember-days";

    int DEFAULT_MAX_DEVICES = 1;
    int DEFAULT_REMEMBER_DAYS = 7;
    int MAX_DEVICE_LIMIT = 20;
    int MAX_REMEMBER_DAYS = 365;

}
